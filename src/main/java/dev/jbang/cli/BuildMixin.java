package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import picocli.CommandLine;

public class BuildMixin {
	public String javaVersion;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's.")
	String main;

	@CommandLine.Option(names = {
			"--module" }, arity = "0..1", fallbackValue = "", description = "Treat resource as a module. Optionally with the given module name", preprocessor = StrictParameterPreprocessor.class)
	String module;

	@CommandLine.Option(names = { "-C", "--compile-option" }, description = "Options to pass to the compiler")
	public List<String> compileOptions;

	@CommandLine.Option(names = { "--manifest" }, parameterConsumer = KeyValueConsumer.class)
	public Map<String, String> manifestOptions;

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (javaVersion != null) {
			opts.add("--java");
			opts.add(javaVersion);
		}
		if (main != null) {
			opts.add("--main");
			opts.add(main);
		}
		if (module != null) {
			opts.add("--module");
			opts.add(module);
		}
		if (compileOptions != null) {
			for (String c : compileOptions) {
				opts.add("-C");
				opts.add(c);
			}
		}
		if (manifestOptions != null) {
			for (Map.Entry<String, String> e : manifestOptions.entrySet()) {
				opts.add("--manifest");
				opts.add(e.getKey() + "=" + e.getValue());
			}
		}
		return opts;
	}
}
