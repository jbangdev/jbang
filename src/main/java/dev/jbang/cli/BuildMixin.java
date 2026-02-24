package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.jbang.devkitman.Jdk;
import dev.jbang.source.Project;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

public class BuildMixin {

	@Spec
	CommandSpec spec; // injected by picocli

	public String javaVersion;

	@CommandLine.Mixin
	JdkProvidersMixin jdkProvidersMixin;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new ParameterException(spec.commandLine(),
					String.format("Invalid version '%s', should be a number optionally followed by a plus sign",
							javaVersion));
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's. Can be a glob pattern using ? and *.")
	String main;

	@CommandLine.Option(names = {
			"--module" }, arity = "0..1", fallbackValue = "", description = "Treat resource as a module. Optionally with the given module name", preprocessor = StrictParameterPreprocessor.class)
	String module;

	@CommandLine.Option(names = { "-C", "--compile-option" }, description = "Options to pass to the compiler")
	public List<String> compileOptions;

	@CommandLine.Option(names = { "--manifest" }, parameterConsumer = KeyValueConsumer.class)
	public Map<String, String> manifestOptions;

	@Option(names = {
			"--integrations" }, description = "Enable integration execution (default: true)", negatable = true)
	public Boolean integrations;

	public Jdk getProjectJdk(Project project) {
		Jdk jdk = project.projectJdk();
		if (javaVersion != null) {
			jdk = jdkProvidersMixin.getJdkManager().getOrInstallJdk(javaVersion);
		}
		return jdk;
	}

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
		if (Boolean.TRUE.equals(integrations)) {
			opts.add("--integrations");
		} else if (Boolean.FALSE.equals(integrations)) {
			opts.add("--no-integrations");
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
		opts.addAll(jdkProvidersMixin.opts());
		return opts;
	}
}
