package dev.jbang.cli;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.source.resolvers.AliasResourceResolver;
import dev.jbang.source.resolvers.LiteralScriptResourceResolver;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script. (default command)")
public class Run extends BaseBuildCommand {

	@CommandLine.Mixin
	public RunMixin runMixin;

	@CommandLine.Option(names = { "-c",
			"--code" }, arity = "0..1", description = "Run the given string as code", preprocessor = StrictParameterPreprocessor.class)
	public Optional<String> literalScript;

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
		jdkProvidersMixin.initJdkProviders();

		userParams = handleRemoteFiles(userParams);
		String scriptOrFile = scriptMixin.scriptOrFile;

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
			prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, script));
		} else {
			if (scriptOrFile != null) {
				prj = pb.build(scriptOrFile);
			} else {
				// HACK it's a crappy way to work around the fact that in the case of
				// interactive we might not have a file to reference but all the code
				// expects one to exist
				prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, ""));
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
		out.println(cmdline);

		return EXIT_EXECUTE;
	}

	void buildAgents(BuildContext ctx) throws IOException {
		Project prj = ctx.getProject();
		Map<String, String> agents = runMixin.javaAgentSlots;
		if (agents == null && prj.getResourceRef() instanceof AliasResourceResolver.AliasedResourceRef) {
			AliasResourceResolver.AliasedResourceRef aref = (AliasResourceResolver.AliasedResourceRef) prj.getResourceRef();
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

	/**
	 * Helper class to peek ahead at `--debug` to pickup --debug=5000, --debug 5000,
	 * --debug *:5000 as debug parameters but not --debug somefile.java
	 */
	static class DebugFallbackConsumer implements CommandLine.IParameterConsumer {

		private static Pattern p = Pattern.compile("(?<address>(.*?:)?(\\d+))|(?<key>\\S*)=(?<value>\\S+)");

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
