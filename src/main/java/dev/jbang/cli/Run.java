package dev.jbang.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jbang.source.Code;
import dev.jbang.source.RunContext;
import dev.jbang.source.resolvers.LiteralScriptResourceResolver;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script.")
public class Run extends BaseBuildCommand {

	@CommandLine.Option(names = { "--java-options" }, description = "A Java runtime option")
	public List<String> javaRuntimeOptions;

	@CommandLine.Option(names = { "-r",
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

	@CommandLine.Option(names = { "--javaagent" }, parameterConsumer = KeyOptionalValueConsumer.class)
	public Map<String, Optional<String>> javaAgentSlots;

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

	@Override
	public Integer doCall() throws IOException {
		if (scriptOrFile == null && ((!interactive && !literalScript.isPresent())
				|| (literalScript.isPresent() && literalScript.get().isEmpty()))) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}

		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = getRunContext();
		Code code;
		if (literalScript.isPresent()) {
			String script;
			if (!literalScript.get().isEmpty()) {
				script = literalScript.get();
				if (scriptOrFile != null) {
					userParams.add(0, scriptOrFile);
					scriptOrFile = null;
				}
			} else {
				script = scriptOrFile;
			}
			Util.verboseMsg("Literal Script to execute: '" + script + "'");
			code = ctx.forResourceRef(LiteralScriptResourceResolver.stringToResourceRef(null, script));
		} else {
			if (scriptOrFile != null) {
				code = ctx.forResource(scriptOrFile);
			} else {
				// HACK it's a crappy way to work around the fact that in the case of
				// interactive we might not have a file to reference but all the code
				// expects one to exist
				code = ctx.forResourceRef(LiteralScriptResourceResolver.stringToResourceRef(null, ""));
			}
		}

		code = prepareArtifacts(code, ctx);

		if (nativeImage && (ctx.isForceJsh() || code.isJShell())) {
			warn(".jsh cannot be used with --native thus ignoring --native.");
			nativeImage = false;
		}

		String cmdline = code.cmdGenerator(ctx).generate();
		debug("run: " + cmdline);
		out.println(cmdline);

		return EXIT_EXECUTE;
	}

	RunContext getRunContext() {
		RunContext ctx = super.getRunContext();
		ctx.setRunCommand(true);
		ctx.setArguments(userParams);
		ctx.setJavaOptions(javaRuntimeOptions);
		ctx.setInteractive(interactive);
		ctx.setEnableAssertions(enableAssertions);
		ctx.setEnableSystemAssertions(enableSystemAssertions);
		ctx.setFlightRecorderString(flightRecorderString);
		ctx.setDebugString(debugString);
		ctx.setClassDataSharing(cds);
		return ctx;
	}

	Code prepareArtifacts(Code code, RunContext ctx) throws IOException {
		code = buildIfNeeded(code, ctx);

		if (javaAgentSlots != null) {
			for (Map.Entry<String, Optional<String>> agentOption : javaAgentSlots.entrySet()) {
				String javaAgent = agentOption.getKey();
				Optional<String> javaAgentOptions = agentOption.getValue();

				RunContext actx = super.getRunContext();
				Code asrc = actx.forResource(javaAgent);
				actx.setJavaAgentOption(javaAgentOptions.orElse(null));
				if (needsJar(asrc, actx)) {
					info("Building javaagent...");
					asrc = buildIfNeeded(asrc, actx);
				}
				ctx.addJavaAgent(asrc, actx);
			}
		}

		return code;
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
