package dev.jbang.source.generators;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.Settings;
import dev.jbang.source.Code;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.source.SourceSet;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JshCmdGenerator extends BaseCmdGenerator {
	protected final SourceSet ss;

	public JshCmdGenerator(SourceSet ss, RunContext ctx) {
		super(ctx);
		this.ss = ss;
	}

	@Override
	protected Code getCode() {
		return ss;
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		String classpath = ctx.resolveClassPath(ss);

		List<String> optionalArgs = new ArrayList<>();

		String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion()
				: ss.getJavaVersion().orElse(null);
		String javacmd;
		javacmd = JavaUtil.resolveInJavaHome("jshell", requestedJavaVersion);

		if (ss.getJarFile() != null && ss.getJarFile().exists()) {
			if (Util.isBlankString(classpath)) {
				classpath = ss.getJarFile().getAbsolutePath();
			} else {
				classpath = ss.getJarFile().getAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
			}
		}

		// NB: See https://github.com/jbangdev/jbang/issues/992 for the reasons why we
		// use the -J flags below

		optionalArgs.add("--execution=local");
		optionalArgs.add("-J--add-modules=ALL-SYSTEM");

		if (!Util.isBlankString(classpath)) {
			optionalArgs.add("--class-path=" + classpath);
			optionalArgs.add("-J--class-path=" + classpath);
		}

		optionalArgs.add("--startup=DEFAULT");

		File tempFile = File.createTempFile("jbang_arguments_", ss.getResourceRef().getFile().getName());

		String defaultImports = "import java.lang.*;\n" +
				"import java.util.*;\n" +
				"import java.io.*;" +
				"import java.net.*;" +
				"import java.math.BigInteger;\n" +
				"import java.math.BigDecimal;\n";
		Util.writeString(tempFile.toPath(),
				defaultImports + generateArgs(ctx.getArguments(), ctx.getProperties()) +
						generateStdInputHelper() +
						generateMain(ctx.getMainClass()));
		if (ctx.getMainClass() != null) {
			if (!ctx.getMainClass().contains(".")) {
				Util.warnMsg("Main class `" + ctx.getMainClass()
						+ "` is in the default package which JShell unfortunately does not support. You can still use JShell to explore the JDK and any dependencies available on the classpath.");
			} else {
				Util.infoMsg("You can run the main class `" + ctx.getMainClass() + "` using: userMain(args)");
			}
		}
		optionalArgs.add("--startup=" + tempFile.getAbsolutePath());

		if (ctx.isDebugEnabled()) {
			Util.warnMsg("debug not possible when running via jshell.");
		}
		if (ctx.isFlightRecordingEnabled()) {
			Util.warnMsg("Java Flight Recording not possible when running via jshell.");
		}

		fullArgs.add(javacmd);
		addAgentsArgs(fullArgs);

		fullArgs.addAll(ctx.getRuntimeOptionsMerged(ss));
		fullArgs.addAll(ctx.getAutoDetectedModuleArguments(ss, requestedJavaVersion));
		fullArgs.addAll(optionalArgs);

		if (ss.isJShell() || ctx.isForceJsh()) {
			for (Source s : ss.getSources()) {
				fullArgs.add(s.getResourceRef().getFile().toString());
			}
		}

		if (!ctx.isInteractive()) {
			File exitFile = File.createTempFile("jbang_exit_", ss.getResourceRef().getFile().getName());
			Util.writeString(exitFile.toPath(), "/exit");
			fullArgs.add(exitFile.toString());
		}

		return fullArgs;
	}

	public static String generateArgs(List<String> args, Map<String, String> properties) {

		String buf = "String[] args = { " +
				args.stream()
					.map(s -> '"' + StringEscapeUtils.escapeJava(s) + '"')
					.collect(Collectors.joining(", "))
				+
				" }" +
				(properties.isEmpty() ? "" : "\n") +
				properties	.entrySet()
							.stream()
							.map(x -> "System.setProperty(\"" + x.getKey() + "\",\"" + x.getValue() + "\");")
							.collect(Collectors.joining("\n"));
		return buf;
	}

	private static String generateStdInputHelper() {
		String buf = "\nStream<String> lines() { return new BufferedReader(new InputStreamReader(System.in)).lines(); }\n";
		buf += "\nStream<String> lines(String path) throws IOException { return Files.lines(Path.of(path)); }\n";
		buf += "/open PRINTING\n";
		return buf;
	}

	private static String generateMain(String main) {
		if (main != null) {
			return "\nint userMain(String[] args) { return " + main + "(args);}\n";
		}
		return "";
	}
}
