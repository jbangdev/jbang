package dev.jbang.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.source.resolvers.LiteralScriptResourceResolver;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script.")
public class Run extends BaseBuildCommand {

	@CommandLine.Option(names = { "-R", "--runtime-option",
			"--java-options" }, description = "Options to pass to the Java runtime")
	public List<String> javaRuntimeOptions;

	@CommandLine.Option(names = {
			"--jfr" }, fallbackValue = "${jbang.run.jfr}", parameterConsumer = KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	public String flightRecorderString;

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "${jbang.run.debug}", parameterConsumer = DebugFallbackConsumer.class, arity = "0..1", description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	public String debugString;

	// should take arguments for package/classes when picocli fixes its flag
	// handling bug in release 4.6.
	// https://docs.oracle.com/cd/E19683-01/806-7930/assert-4/index.html
	@CommandLine.Option(names = { "--enableassertions", "--ea" }, description = "Enable assertions")
	public boolean enableAssertions;

	@CommandLine.Option(names = { "--enablesystemassertions", "--esa" }, description = "Enable system assertions")
	public boolean enableSystemAssertions;

	@CommandLine.Option(names = { "--manifest" }, parameterConsumer = KeyValueConsumer.class)
	public Map<String, String> manifestOptions;

	@CommandLine.Option(names = { "--javaagent" }, parameterConsumer = KeyValueConsumer.class)
	public Map<String, String> javaAgentSlots;

	@CommandLine.Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	@CommandLine.Option(names = { "-i", "--interactive" }, description = "Activate interactive mode")
	public boolean interactive;

	@CommandLine.Option(names = { "-c",
			"--code" }, arity = "0..1", description = "Run the given string as code", preprocessor = StrictParameterPreprocessor.class)
	public Optional<String> literalScript;

	@CommandLine.Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	public List<String> userParams = new ArrayList<>();

	protected void requireScriptArgument() {
		if (scriptMixin.scriptOrFile == null && ((!interactive && !literalScript.isPresent())
				|| (literalScript.isPresent() && literalScript.get().isEmpty()))) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

	private void rewriteScriptArguments() {
		if (literalScript.isPresent() && !literalScript.get().isEmpty() && scriptMixin.scriptOrFile != null) {
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
		javaAgentSlots = handleRemoteFiles(javaAgentSlots);
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
		CmdGeneratorBuilder genb = prj.codeBuilder(ctx).build();

		buildAgents(ctx);

		String cmdline = updateGeneratorForRun(genb).build().generate();

		Util.verboseMsg("run: " + cmdline);
		out.println(cmdline);

		return EXIT_EXECUTE;
	}

	void buildAgents(BuildContext ctx) throws IOException {
		if (javaAgentSlots != null) {
			if (javaRuntimeOptions == null) {
				javaRuntimeOptions = new ArrayList<>();
			}
			for (Map.Entry<String, String> agentOption : javaAgentSlots.entrySet()) {
				String javaAgent = agentOption.getKey();
				String javaAgentOptions = agentOption.getValue();
				ProjectBuilder apb = createBaseProjectBuilder();
				Project aprj = apb.build(javaAgent);
				BuildContext actx = ctx.forSubProject(aprj, "agents");
				aprj.codeBuilder(actx).build();
				javaRuntimeOptions.addAll(javaAgentOptions(actx, javaAgentOptions));
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
			.runtimeOptions(javaRuntimeOptions)
			.mainClass(buildMixin.main)
			.interactive(interactive)
			.enableAssertions(enableAssertions)
			.enableSystemAssertions(enableSystemAssertions)
			.flightRecorderString(flightRecorderString)
			.debugString(debugString)
			.classDataSharing(cds);

		return gb;
	}

	private static List<String> handleRemoteFiles(List<String> args) {
		return args.stream().map(Run::substituteRemote).collect(Collectors.toList());
	}

	private static Map<String, String> handleRemoteFiles(Map<String, String> slots) {
		if (slots != null) {
			Map<String, String> result = new HashMap<>();
			slots.forEach((key, value) -> result.put(key, substituteRemote(value)));
			return result;
		} else {
			return null;
		}
	}

	private static final Pattern subUrlPattern = Pattern.compile("^(%?%https?://.+$)|(%?%\\{https?://[^}]+})");

	private static String substituteRemote(String arg) {
		if (arg == null) {
			return null;
		}
		return Util.replaceAll(subUrlPattern, arg, m -> {
			String txt = m.group().substring(1);
			if (txt.startsWith("%")) {
				return Matcher.quoteReplacement(txt);
			}
			if (txt.startsWith("{") && txt.endsWith("}")) {
				txt = txt.substring(1, txt.length() - 1);
			}
			try {
				return Matcher.quoteReplacement(Util.downloadAndCacheFile(txt).toString());
			} catch (IOException e) {
				throw new ExitException(EXIT_INVALID_INPUT, "Error substituting remote file: " + txt, e);
			}
		});
	}

	/**
	 * Helper class to peek ahead at `--debug` to pickup --debug=5000, --debug 5000,
	 * --debug *:5000 as debug parameters but not --debug somefile.java
	 */
	static class DebugFallbackConsumer extends PatternFallbackConsumer {
		private static final Pattern p = Pattern.compile("(.*?:)?(\\d+)");

		@Override
		protected Pattern getValuePattern() {
			return p;
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
