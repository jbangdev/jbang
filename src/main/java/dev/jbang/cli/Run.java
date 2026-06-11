package dev.jbang.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.resources.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.util.DigestUtil;
import dev.jbang.util.LockFileUtil;
import dev.jbang.util.Util;

@CommandDefinition(name = "run", description = "Builds and runs provided script. (default command)", generateHelp = true, stopAtFirstPositional = true, helpGroup = "Essentials")
public class Run extends BaseBuildCommand {

	/**
	 * A PrintStream that always writes to the real stdout, bypassing any
	 * System.setOut() redirection. Used to output the command line for the parent
	 * process to execute.
	 */
	public PrintStream realOut = new PrintStream(new FileOutputStream(FileDescriptor.out));

	@Mixin
	public RunMixin runMixin;

	@Option(shortName = 'c', name = "code", fallbackValue = "", description = "Run the given string as code")
	public String literalScript;

	@Option(name = "locked", description = "Lock behavior: none, lenient, strict", defaultValue = "lenient")
	String lockModeStr;

	@Option(name = "lock-file", description = "Path to lock file (default: .jbang.lock)")
	Path lockFile;

	@Arguments(paramLabel = "userParams", index = "1..*", arity = "0..*", description = "Parameters for the script")
	public List<String> userParams;

	@Override
	public void afterParse() {
		super.afterParse();
		runMixin.resolveAfterParse();
		if (userParams == null) {
			userParams = new ArrayList<>();
		}
	}

	protected void requireScriptArgument() {
		if (scriptMixin.scriptOrFile == null && ((runMixin.interactive != Boolean.TRUE && literalScript == null)
				|| (literalScript != null && literalScript.isEmpty()))) {
			if (commandInvocation != null) {
				System.err.println(commandInvocation.getHelpInfo());
			}
			throw new ExitException(EXIT_INVALID_INPUT, "Missing required parameter: '<scriptOrFile>'");
		}
	}

	private void rewriteScriptArguments() {
		if (literalScript != null && !literalScript.isEmpty()
				&& scriptMixin.scriptOrFile != null) {
			List<String> args = new ArrayList<>();
			args.add(scriptMixin.scriptOrFile);
			if (userParams != null) {
				args.addAll(userParams);
			}
			userParams = args;
		}
	}

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		rewriteScriptArguments();
		userParams = handleRemoteFiles(userParams);
		String script = scriptMixin.scriptOrFile;

		LockMode lockMode = LockMode.valueOf(lockModeStr);

		DigestUtil.RefWithChecksum refWithChecksum = script != null ? DigestUtil.splitRefAndChecksum(script) : null;
		if (refWithChecksum != null) {
			script = refWithChecksum.ref;
		}

		Path effectiveLockFile = DigestUtil.resolveLockFile(lockFile, script);
		List<String> preLockSources = Collections.emptyList();
		if (lockMode != LockMode.none && script != null && Files.exists(effectiveLockFile)) {
			preLockSources = LockFileUtil.readSources(effectiveLockFile, script);
		}

		ProjectBuilder pb = createProjectBuilderForRun();
		if (!preLockSources.isEmpty()) {
			pb.lockedSourcesOverride(preLockSources);
		}

		Project prj;
		if (literalScript != null) {
			String code;
			if (!literalScript.isEmpty()) {
				code = literalScript;
			} else {
				code = script;
			}
			Util.verboseMsg("Literal Script to execute: '" + code + "'");
			prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, code, scriptMixin.getForceType()));
		} else {
			if (script != null) {
				prj = pb.build(script);
			} else {
				// HACK it's a crappy way to work around the fact that in the case of
				// interactive we might not have a file to reference but all the code
				// expects one to exist
				prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, "", scriptMixin.getForceType()));
			}
		}

		if (literalScript == null && script != null) {
			verifyLockConstraints(prj, script, refWithChecksum, effectiveLockFile, lockMode);
		}

		if (Boolean.TRUE.equals(nativeMixin.nativeImage)
				&& (scriptMixin.getForceType() == Source.Type.jshell || prj.isJShell())) {
			warn(".jsh cannot be used with --native thus ignoring --native.");
			prj.setNativeImage(false);
		}

		BuildContext ctx = BuildContext.forProject(prj, getBuildDir());
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
			.classDataSharing(runMixin.getCds());

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

	private void verifyLockConstraints(Project prj, String scriptOrFile,
			DigestUtil.RefWithChecksum refWithChecksum, Path effectiveLockFile, LockMode lockMode) throws IOException {
		String resolvedLocation = prj.getResourceRef().getFile().toAbsolutePath().toString();
		String actualDigest = DigestUtil.digestResource(prj, "sha256");
		String lockDigest = null;
		List<String> lockSources = Collections.emptyList();
		List<String> lockDeps = Collections.emptyList();
		Map<String, String> lockDepDigests = Collections.emptyMap();
		boolean hasLockFile = Files.exists(effectiveLockFile);
		if (lockMode != LockMode.none && hasLockFile) {
			lockDigest = LockFileUtil.readDigest(effectiveLockFile, scriptOrFile);
			lockSources = LockFileUtil.readSources(effectiveLockFile, scriptOrFile);
			lockDeps = LockFileUtil.readDeps(effectiveLockFile, scriptOrFile);
			lockDepDigests = LockFileUtil.readDepDigests(effectiveLockFile, scriptOrFile);
		}

		if (refWithChecksum != null && refWithChecksum.checksum != null) {
			DigestUtil.verifyDigestSpec(actualDigest, refWithChecksum.checksum,
					"reference checksum for " + scriptOrFile + " (resolved " + resolvedLocation + ")");
		}
		if (lockMode != LockMode.none && hasLockFile) {
			if (lockMode == LockMode.strict && lockDigest == null) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"Lock verification failed for " + scriptOrFile + " (missing lock entry in "
								+ effectiveLockFile
								+ ").\nPossible security issue: expected lock data is absent.\n"
								+ "What to do: inspect the lock file path, regenerate with `jbang lock "
								+ scriptOrFile
								+ "` only if trusted, or stop and review changes.",
						null);
			}
			if (lockDigest != null) {
				DigestUtil.verifyDigestSpec(actualDigest, lockDigest,
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
				DigestUtil.verifyLockedSet("sources", scriptOrFile, expected, actual);
			}
			if (!lockDeps.isEmpty()) {
				verifyLockedDeps(prj, scriptOrFile, lockDeps, lockDepDigests, lockMode);
			}
		}
	}

	private void verifyLockedDeps(Project prj, String scriptOrFile,
			List<String> lockDeps, Map<String, String> lockDepDigests, LockMode lockMode) {
		Set<String> expectedDeps = new LinkedHashSet<>(lockDeps);
		BuildContext depCtx = BuildContext.forProject(prj);
		Set<String> actualDeps = depCtx
			.resolveClassPath()
			.getArtifacts()
			.stream()
			.map(a -> a.getCoordinate() == null ? "" : a.getCoordinate().toCanonicalForm())
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		DigestUtil.verifyLockedSet("dependency graph", scriptOrFile, expectedDeps, actualDeps);

		if (lockMode == LockMode.strict && !expectedDeps.isEmpty()
				&& lockDepDigests.size() < expectedDeps.size()) {
			Set<String> missing = new LinkedHashSet<>(expectedDeps);
			missing.removeAll(lockDepDigests.keySet());
			throw new ExitException(EXIT_INVALID_INPUT,
					"Lock verification failed for " + scriptOrFile
							+ " (strict mode requires dependency digests for all locked dependencies).\n"
							+ "Possible security issue: lock file is incomplete or modified.\n"
							+ "Missing digest entries for: " + missing + "\n"
							+ "What to do: regenerate with `jbang lock " + scriptOrFile
							+ "` only if trusted, otherwise review lock/source history.",
					null);
		}

		if (!lockDepDigests.isEmpty()) {
			Map<String, String> actualDepDigests = depCtx.resolveClassPath()
				.getArtifacts()
				.stream()
				.filter(a -> a.getCoordinate() != null)
				.collect(Collectors.toMap(
						a -> a.getCoordinate().toCanonicalForm(),
						a -> DigestUtil.digestPath(a.getFile(), "sha256"),
						(a, b) -> a, LinkedHashMap::new));
			for (Map.Entry<String, String> e : lockDepDigests.entrySet()) {
				String coord = e.getKey();
				String expected = e.getValue();
				String actual = actualDepDigests.get(coord);
				if (actual == null) {
					throw new ExitException(EXIT_INVALID_INPUT,
							"Lock verification failed for " + scriptOrFile
									+ " (dependency artifact missing for locked digest).\n"
									+ "Dependency: " + coord + "\n"
									+ "Possible security/integrity issue: resolved dependency set differs from lock.\n"
									+ "What to do: inspect dependency changes; regenerate lock only if trusted.",
							null);
				}
				DigestUtil.verifyDigestSpec(actual, expected,
						"lock dependency digest for " + coord + " in " + scriptOrFile,
						lockMode != LockMode.strict);
			}
		}
	}

	enum LockMode {
		none, lenient, strict
	}
}
