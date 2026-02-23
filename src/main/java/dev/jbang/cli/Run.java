package dev.jbang.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.resources.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.util.LockFileUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script. (default command)")
public class Run extends BaseBuildCommand {

	private static final int MIN_DIGEST_PREFIX_LENGTH = 12;

	@CommandLine.Mixin
	public RunMixin runMixin;

	@CommandLine.Option(names = { "-c",
			"--code" }, arity = "0..1", description = "Run the given string as code", preprocessor = StrictParameterPreprocessor.class)
	public Optional<String> literalScript;

	@CommandLine.Option(names = { "--locked" }, description = "Lock behavior: none, lenient, strict")
	LockMode lockMode = LockMode.lenient;

	@CommandLine.Option(names = { "--lock-file" }, description = "Path to lock file (default: .jbang.lock)")
	Path lockFile;

	@CommandLine.Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	public List<String> userParams = new ArrayList<>();

	protected void requireScriptArgument() {
		if (scriptMixin.scriptOrFile == null && ((runMixin.interactive != Boolean.TRUE && !literalScript.isPresent())
				|| (literalScript.isPresent() && literalScript.get().isEmpty()))) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

	private void rewriteScriptArguments() {
		if (literalScript.isPresent() && !literalScript.get().isEmpty()
				&& scriptMixin.scriptOrFile != null) {
			List<String> args = new ArrayList<>();
			args.add(scriptMixin.scriptOrFile);
			args.addAll(userParams);
			userParams = args;
		}
	}

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		rewriteScriptArguments();

		userParams = handleRemoteFiles(userParams);
		String scriptOrFile = scriptMixin.scriptOrFile;
		RefWithChecksum refWithChecksum = scriptOrFile != null ? splitRefAndChecksum(scriptOrFile) : null;
		if (refWithChecksum != null) {
			scriptOrFile = refWithChecksum.ref;
		}

		Path effectiveLockFile = resolveLockFile(scriptOrFile);
		List<String> preLockSources = Collections.emptyList();
		if (lockMode != LockMode.none && scriptOrFile != null && java.nio.file.Files.exists(effectiveLockFile)) {
			preLockSources = LockFileUtil.readSources(effectiveLockFile, scriptOrFile);
		}

		ProjectBuilder pb = createProjectBuilderForRun();
		if (!preLockSources.isEmpty()) {
			pb.lockedSourcesOverride(preLockSources);
		}

		Project prj;
		if (literalScript.isPresent()) {
			String script;
			if (!literalScript.get().isEmpty()) {
				script = literalScript.get();
			} else {
				script = scriptOrFile;
			}
			Util.verboseMsg("Literal Script to execute: '" + script + "'");
			prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, script, scriptMixin.forceType));
		} else {
			if (scriptOrFile != null) {
				prj = pb.build(scriptOrFile);
			} else {
				// HACK it's a crappy way to work around the fact that in the case of
				// interactive we might not have a file to reference but all the code
				// expects one to exist
				prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, "", scriptMixin.forceType));
			}
		}

		if (!literalScript.isPresent() && scriptOrFile != null) {
			String resolvedLocation = prj.getResourceRef().getFile().toAbsolutePath().toString();
			String actualDigest = digestResource(prj, "sha256");
			String lockDigest = null;
			List<String> lockSources = Collections.emptyList();
			List<String> lockDeps = Collections.emptyList();
			Map<String, String> lockDepDigests = Collections.emptyMap();
			boolean hasLockFile = java.nio.file.Files.exists(effectiveLockFile);
			if (lockMode != LockMode.none && hasLockFile) {
				lockDigest = LockFileUtil.readDigest(effectiveLockFile, scriptOrFile);
				lockSources = LockFileUtil.readSources(effectiveLockFile, scriptOrFile);
				lockDeps = LockFileUtil.readDeps(effectiveLockFile, scriptOrFile);
				lockDepDigests = LockFileUtil.readDepDigests(effectiveLockFile, scriptOrFile);
			}

			if (refWithChecksum != null && refWithChecksum.checksum != null) {
				verifyDigestSpec(actualDigest, refWithChecksum.checksum,
						"reference checksum for " + scriptOrFile + " (resolved " + resolvedLocation + ")");
			}
			if (lockMode != LockMode.none && hasLockFile) {
				if (lockMode == LockMode.strict && lockDigest == null) {
					throw new ExitException(EXIT_INVALID_INPUT,
							"No lock entry for reference: " + scriptOrFile + " in " + effectiveLockFile, null);
				}
				if (lockDigest != null) {
					verifyDigestSpec(actualDigest, lockDigest,
							"lockfile for " + scriptOrFile + " (resolved " + resolvedLocation + ")",
							lockMode != LockMode.strict);
				}
				if (!lockSources.isEmpty()) {
					Set<String> expected = new LinkedHashSet<>(lockSources);
					Set<String> actual = prj.getMainSourceSet()
						.getSources()
						.stream()
						.map(s -> s.getOriginalResource() == null ? "" : s.getOriginalResource())
						.collect(Collectors.toCollection(LinkedHashSet::new));
					verifyLockedSet("sources", scriptOrFile, expected, actual);
				}
				if (!lockDeps.isEmpty()) {
					Set<String> expectedDeps = new LinkedHashSet<>(lockDeps);
					BuildContext depCtx = BuildContext.forProject(prj);
					Set<String> actualDeps = depCtx
						.resolveClassPath()
						.getArtifacts()
						.stream()
						.map(a -> a.getCoordinate() == null ? "" : a.getCoordinate().toCanonicalForm())
						.filter(s -> !s.isEmpty())
						.collect(Collectors.toCollection(LinkedHashSet::new));
					verifyLockedSet("dependency graph", scriptOrFile, expectedDeps, actualDeps);

					if (lockMode == LockMode.strict && !expectedDeps.isEmpty() && lockDepDigests.size() < expectedDeps.size()) {
						Set<String> missing = new LinkedHashSet<>(expectedDeps);
						missing.removeAll(lockDepDigests.keySet());
						throw new ExitException(EXIT_INVALID_INPUT,
								"Strict lock requires dependency digests for all locked deps in " + scriptOrFile + ". Missing: " + missing,
								null);
					}

					if (!lockDepDigests.isEmpty()) {
						Map<String, String> actualDepDigests = depCtx.resolveClassPath().getArtifacts().stream()
							.filter(a -> a.getCoordinate() != null)
							.collect(Collectors.toMap(a -> a.getCoordinate().toCanonicalForm(),
									a -> digestPath(a.getFile(), "sha256"), (a, b) -> a, LinkedHashMap::new));
						for (Map.Entry<String, String> e : lockDepDigests.entrySet()) {
							String coord = e.getKey();
							String expected = e.getValue();
							String actual = actualDepDigests.get(coord);
							if (actual == null) {
								throw new ExitException(EXIT_INVALID_INPUT,
										"Locked dependency digest missing resolved artifact for " + coord + " in " + scriptOrFile,
										null);
							}
							verifyDigestSpec(actual, expected,
									"lock dependency digest for " + coord + " in " + scriptOrFile,
									lockMode != LockMode.strict);
						}
					}
				}
			}

		}

		if (Boolean.TRUE.equals(nativeMixin.nativeImage)
				&& (scriptMixin.forceType == Source.Type.jshell || prj.isJShell())) {
			warn(".jsh cannot be used with --native thus ignoring --native.");
			prj.setNativeImage(false);
		}

		BuildContext ctx = BuildContext.forProject(prj, buildDir);
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		buildAgents(ctx);

		String cmdline = updateGeneratorForRun(genb).build().generate();

		Util.verboseMsg("run: " + cmdline);
		realOut.println(cmdline);

		return EXIT_EXECUTE;
	}

	void buildAgents(BuildContext ctx) throws IOException {
		Project prj = ctx.getProject();
		Map<String, String> agents = runMixin.javaAgentSlots;
		if (agents == null && prj.getResourceRef() instanceof AliasResourceResolver.AliasedResourceRef) {
			AliasResourceResolver.AliasedResourceRef aref = (AliasResourceResolver.AliasedResourceRef) prj
				.getResourceRef();
			if (aref.getAlias().javaAgents != null) {
				Map<String, String> tmpAgents = new HashMap<>();
				aref.getAlias().javaAgents.forEach(a -> tmpAgents.put(a.agentRef, a.options));
				agents = tmpAgents;
			}
		}
		if (agents != null) {
			if (runMixin.javaRuntimeOptions == null) {
				runMixin.javaRuntimeOptions = new ArrayList<>();
			}
			agents = handleRemoteFiles(agents);
			for (Map.Entry<String, String> agentOption : agents.entrySet()) {
				String javaAgent = agentOption.getKey();
				String javaAgentOptions = agentOption.getValue();
				ProjectBuilder apb = createBaseProjectBuilder();
				Project aprj = apb.build(javaAgent);
				BuildContext actx = BuildContext.forProject(aprj);
				Project.codeBuilder(actx).build();
				runMixin.javaRuntimeOptions.addAll(javaAgentOptions(actx, javaAgentOptions));
			}
		}
	}

	private List<String> javaAgentOptions(BuildContext agentCtx, String agentOptions) {
		return Collections.singletonList(
				"-javaagent:" + agentCtx.getJarFile() + (agentOptions != null ? "=" + agentOptions : ""));
	}

	ProjectBuilder createProjectBuilderForRun() {
		return createBaseProjectBuilder();
	}

	CmdGeneratorBuilder updateGeneratorForRun(CmdGeneratorBuilder gb) {
		gb
			.setArguments(userParams)
			.runtimeOptions(runMixin.javaRuntimeOptions)
			.mainClass(buildMixin.main)
			.moduleName(buildMixin.module)
			.interactive(runMixin.interactive)
			.enableAssertions(runMixin.enableAssertions)
			.enableSystemAssertions(runMixin.enableSystemAssertions)
			.flightRecorderString(runMixin.flightRecorderString)
			.debugString(runMixin.debugString)
			.classDataSharing(runMixin.cds);

		return gb;
	}

	private static List<String> handleRemoteFiles(List<String> args) {
		return args.stream().map(Util::substituteRemote).collect(Collectors.toList());
	}

	private static Map<String, String> handleRemoteFiles(Map<String, String> slots) {
		Map<String, String> result = new HashMap<>();
		slots.forEach((key, value) -> result.put(key, Util.substituteRemote(value)));
		return result;
	}

	enum LockMode {
		none, lenient, strict
	}

	private Path resolveLockFile(String scriptOrFile) {
		if (lockFile != null) {
			return lockFile;
		}
		if (scriptOrFile != null) {
			java.nio.file.Path p = java.nio.file.Paths.get(scriptOrFile);
			java.nio.file.Path candidate = p.isAbsolute() ? p : Util.getCwd().resolve(p);
			if (java.nio.file.Files.exists(candidate) && java.nio.file.Files.isRegularFile(candidate)) {
				return java.nio.file.Paths.get(scriptOrFile + ".lock");
			}
		}
		return Util.getCwd().resolve(".jbang.lock");
	}

	static final class RefWithChecksum {
		final String ref;
		final String checksum;

		RefWithChecksum(String ref, String checksum) {
			this.ref = ref;
			this.checksum = checksum;
		}
	}

	static RefWithChecksum splitRefAndChecksum(String ref) {
		int idx = ref.lastIndexOf('#');
		if (idx < 0 || idx == ref.length() - 1) {
			return new RefWithChecksum(ref, null);
		}
		String suffix = ref.substring(idx + 1);
		String digest = suffix.contains(":") ? suffix : "sha256:" + suffix;
		return new RefWithChecksum(ref.substring(0, idx), digest);
	}


	private static String digestPath(java.nio.file.Path path, String algorithm) {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT));
			try (InputStream in = java.nio.file.Files.newInputStream(path)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					md.update(buffer, 0, read);
				}
			}
			return algorithm.toLowerCase(Locale.ROOT) + ":" + toHex(md.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to digest dependency artifact: " + path, e);
		}
	}

	private static String digestResource(Project prj, String algorithm) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT));
			try (InputStream in = prj.getResourceRef().getInputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					md.update(buffer, 0, read);
				}
			}
			return algorithm.toLowerCase(Locale.ROOT) + ":" + toHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unsupported digest algorithm: " + algorithm, e);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	static void verifyLockedSet(String kind, String ref, Set<String> expected, Set<String> actual) {
		if (!actual.equals(expected)) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Locked " + kind + " mismatch for " + ref + ". Expected " + expected + " but got " + actual,
					null);
		}
	}

	static void verifyDigestSpec(String actualDigest, String expectedDigest, String source) {
		verifyDigestSpec(actualDigest, expectedDigest, source, true);
	}

	static void verifyDigestSpec(String actualDigest, String expectedDigest, String source, boolean allowPrefix) {
		String[] actualParts = actualDigest.split(":", 2);
		String[] expectedParts = expectedDigest.split(":", 2);
		if (expectedParts.length != 2) {
			throw new ExitException(EXIT_INVALID_INPUT, "Invalid digest format from " + source + ": " + expectedDigest,
					null);
		}
		if (!actualParts[0].equalsIgnoreCase(expectedParts[0])) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Digest algorithm mismatch in " + source + ": expected " + expectedParts[0] + ", got "
							+ actualParts[0],
					null);
		}
		String expectedHex = expectedParts[1].toLowerCase(Locale.ROOT);
		String actualHex = actualParts[1].toLowerCase(Locale.ROOT);
		if (allowPrefix && expectedHex.length() < MIN_DIGEST_PREFIX_LENGTH) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Digest prefix too short in " + source + ". Use at least " + MIN_DIGEST_PREFIX_LENGTH
							+ " hex characters.",
					null);
		}
		if (allowPrefix ? !actualHex.startsWith(expectedHex) : !actualHex.equals(expectedHex)) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Digest mismatch in " + source + ": expected " + expectedDigest + ", got " + actualDigest, null);
		}
	}

	/**
	 * Helper class to peek ahead at `--debug` to pickup --debug=5000, --debug 5000,
	 * --debug *:5000 as debug parameters but not --debug somefile.java
	 */
	static class DebugFallbackConsumer implements CommandLine.IParameterConsumer {

		final private static Pattern p = Pattern
			.compile("(?<address>(.*?:)?(\\d+\\??))|(?<key>\\S*)=(?<value>\\S+\\??)");

		@Override
		public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec,
				CommandLine.Model.CommandSpec commandSpec) {
			String arg = args.peek();
			Matcher m = p.matcher(arg);

			if (!m.matches()) {
				m = p.matcher(((CommandLine.Model.OptionSpec) argSpec).fallbackValue());
			} else {
				args.pop();
			}

			if (m.matches()) {
				Map<String, String> kv = argSpec.getValue();

				if (kv == null) {
					kv = new LinkedHashMap<>();
				}

				String address = m.group("address");
				if (address != null) {
					kv.put("address", address);
				} else {
					kv.put(m.group("key"), m.group("value"));
				}
				argSpec.setValue(kv);
			}
		}
	}

	/**
	 * Helper class to peek ahead at `--jfr` to pickup x=y,t=y but not --jfr
	 * somefile.java
	 */
	static class KeyValueFallbackConsumer extends PatternFallbackConsumer {
		private static final Pattern p = Pattern.compile("(\\S*?)=(\\S+)");

		@Override
		protected Pattern getValuePattern() {
			return p;
		}

	}

	static abstract class PatternFallbackConsumer implements CommandLine.IParameterConsumer {

		protected abstract Pattern getValuePattern();

		@Override
		public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec,
				CommandLine.Model.CommandSpec commandSpec) {
			Matcher m = getValuePattern().matcher(args.peek());
			if (m.matches()) {
				argSpec.setValue(args.pop());
			} else {
				String val, name;
				if (argSpec.isOption()) {
					CommandLine.Model.OptionSpec opt = (CommandLine.Model.OptionSpec) argSpec;
					name = opt.longestName();
					val = opt.fallbackValue();
				} else {
					name = argSpec.paramLabel();
					val = null;
				}
				try {
					argSpec.setValue(val);
				} catch (Exception badValue) {
					throw new CommandLine.InitializationException("Value for " + name + " must be an string", badValue);
				}
			}
		}
	}
}
