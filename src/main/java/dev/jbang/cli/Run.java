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

	@CommandLine.Mixin
	public RunMixin runMixin;

	@CommandLine.Option(names = { "-c",
			"--code" }, arity = "0..1", description = "Run the given string as code", preprocessor = StrictParameterPreprocessor.class)
	public Optional<String> literalScript;

	@CommandLine.Option(names = { "--verify" }, description = "Verify script content with digest, e.g. sha256:abc123")
	String verifyDigest;

	@CommandLine.Option(names = { "--locked" }, description = "Require matching digest from lock file")
	boolean locked;

	@CommandLine.Option(names = { "--lock-file" }, description = "Path to lock file (default: .jbang.lock)")
	Path lockFile;

	@CommandLine.Option(names = { "--lock-write" }, description = "Write/update digest entry in lock file")
	boolean lockWrite;

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

		ProjectBuilder pb = createProjectBuilderForRun();

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
			Path effectiveLockFile = lockFile != null ? lockFile : Util.getCwd().resolve(".jbang.lock");
			String actualDigest = digestResource(prj, "sha256");
			String lockDigest = null;
			List<String> lockSources = Collections.emptyList();
			if (locked || lockWrite) {
				lockDigest = LockFileUtil.readDigest(effectiveLockFile, scriptOrFile);
				lockSources = LockFileUtil.readSources(effectiveLockFile, scriptOrFile);
			}

			if (verifyDigest != null) {
				verifyDigestSpec(actualDigest, verifyDigest, "--verify");
			}
			if (refWithChecksum != null && refWithChecksum.checksum != null) {
				verifyDigestSpec(actualDigest, refWithChecksum.checksum, "reference checksum");
			}
			if (locked) {
				if (lockDigest == null) {
					throw new ExitException(EXIT_INVALID_INPUT,
							"No lock entry for reference: " + scriptOrFile + " in " + effectiveLockFile, null);
				}
				verifyDigestSpec(actualDigest, lockDigest, "lockfile");
				if (!lockSources.isEmpty()) {
					Set<String> expected = new LinkedHashSet<>(lockSources);
					Set<String> actual = prj.getMainSourceSet().getSources().stream()
							.map(s -> s.getOriginalResource() == null ? "" : s.getOriginalResource())
							.collect(Collectors.toCollection(LinkedHashSet::new));
					if (!actual.equals(expected)) {
						throw new ExitException(EXIT_INVALID_INPUT,
								"Locked sources mismatch for " + scriptOrFile + ". Expected " + expected + " but got " + actual,
								null);
					}
				}
			}
			if (lockWrite) {
				LockFileUtil.writeDigest(effectiveLockFile, scriptOrFile, actualDigest);
				info("Updated lock entry for " + scriptOrFile + " in " + effectiveLockFile);
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

	static void verifyDigestSpec(String actualDigest, String expectedDigest, String source) {
		String[] actualParts = actualDigest.split(":", 2);
		String[] expectedParts = expectedDigest.split(":", 2);
		if (expectedParts.length != 2) {
			throw new ExitException(EXIT_INVALID_INPUT, "Invalid digest format from " + source + ": " + expectedDigest, null);
		}
		if (!actualParts[0].equalsIgnoreCase(expectedParts[0])) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Digest algorithm mismatch in " + source + ": expected " + expectedParts[0] + ", got " + actualParts[0], null);
		}
		String expectedHex = expectedParts[1].toLowerCase(Locale.ROOT);
		String actualHex = actualParts[1].toLowerCase(Locale.ROOT);
		if (expectedHex.length() < 12) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Digest prefix too short in " + source + ". Use at least 12 hex characters.", null);
		}
		if (!actualHex.startsWith(expectedHex)) {
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
