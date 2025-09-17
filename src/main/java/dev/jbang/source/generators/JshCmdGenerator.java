package dev.jbang.source.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.devkitman.Jdk;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JshCmdGenerator extends BaseCmdGenerator<JshCmdGenerator> {
	private List<String> runtimeOptions = Collections.emptyList();
	private boolean interactive;
	private String mainClass;

	public JshCmdGenerator runtimeOptions(List<String> runtimeOptions) {
		if (runtimeOptions != null) {
			this.runtimeOptions = runtimeOptions;
		} else {
			this.runtimeOptions = Collections.emptyList();
		}
		return this;
	}

	public JshCmdGenerator interactive(boolean interactive) {
		this.interactive = interactive;
		return this;
	}

	public JshCmdGenerator mainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public JshCmdGenerator(BuildContext ctx) {
		super(ctx);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		Project project = ctx.getProject();
		String classpath = ctx.resolveClassPath().getClassPath();

		List<String> optionalArgs = new ArrayList<>();

		Jdk jdk = project.projectJdk();
		String javacmd = JavaUtil.resolveInJavaHome("jshell", jdk);

		// NB: See https://github.com/jbangdev/jbang/issues/992 for the reasons why we
		// use the -J flags below

		if (project.enablePreview()) {
			// jshell does not seem to automaticall pass enable-preview to runtime/compiler
			optionalArgs.add("--enable-preview");
			// only required because --execution=local cause it to break otherwise.
			optionalArgs.add("-J--enable-preview");
			optionalArgs.add("-C--enable-preview");
		}

		optionalArgs.add("--execution=local");
		optionalArgs.add("-J--add-modules=ALL-SYSTEM");

		if (!Util.isBlankString(classpath)) {
			optionalArgs.add("--class-path=" + classpath);
			optionalArgs.add("-J--class-path=" + classpath);
		}

		optionalArgs.add("--startup=DEFAULT");

		Path tempFile = Files.createTempFile("jbang_arguments_",
				project.getResourceRef().getFile().getFileName().toString());

		String defaultImports = "import java.lang.*;\n" +
				"import java.util.*;\n" +
				"import java.io.*;" +
				"import java.net.*;" +
				"import java.math.BigInteger;\n" +
				"import java.math.BigDecimal;\n";
		String main = Optional.ofNullable(mainClass).orElse(project.getMainClass());
		Util.writeString(tempFile,
				defaultImports + generateArgs(arguments, project.getProperties()) +
						generateStdInputHelper() +
						generateMain(main));
		if (main != null) {
			if (!main.contains(".")) {
				Util.warnMsg("Main class `" + main
						+ "` is in the default package which JShell unfortunately does not support. You can still use JShell to explore the JDK and any dependencies available on the classpath.");
			} else {
				Util.infoMsg("You can run the main class `" + main + "` using: userMain(args)");
			}
		}
		optionalArgs.add("--startup=" + tempFile.toAbsolutePath());

		if (debugString != null) {
			Util.warnMsg("debug not possible when running via jshell.");
		}
		if (flightRecorderString != null) {
			Util.warnMsg("Java Flight Recording not possible when running via jshell.");
		}

		fullArgs.add(javacmd);

		fullArgs.addAll(jshellOpts(project.getRuntimeOptions()));
		fullArgs.addAll(jshellOpts(runtimeOptions));
		fullArgs.addAll(ctx.resolveClassPath()
			.getAutoDectectedModuleArguments(jdk));
		fullArgs.addAll(optionalArgs);

		if (project.isJShell()) {
			ArrayList<ResourceRef> revSources = new ArrayList<>(project.getMainSourceSet().getSources());
			Collections.reverse(revSources);
			for (ResourceRef s : revSources) {
				fullArgs.add(s.getFile().toString());
			}
		}

		if (!interactive) {
			Path exitFile = Files.createTempFile("jbang_exit_",
					project.getResourceRef().getFile().getFileName().toString());
			Util.writeString(exitFile, "/exit");
			fullArgs.add(exitFile.toString());
		}

		return fullArgs;
	}

	private Collection<String> jshellOpts(List<String> opts) {
		return opts.stream().map(it -> "-J" + it).collect(Collectors.toList());
	}

	public static String generateArgs(List<String> args, Map<String, String> properties) {

		String buf = "String[] args = { " +
				args.stream()
					.map(s -> '"' + StringEscapeUtils.escapeJava(s) + '"')
					.collect(Collectors.joining(", "))
				+
				" }" +
				(properties.isEmpty() ? "" : "\n") +
				properties.entrySet()
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
