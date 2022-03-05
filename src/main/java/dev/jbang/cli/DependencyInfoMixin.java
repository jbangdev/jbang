package dev.jbang.cli;

import java.util.List;
import java.util.Map;

import picocli.CommandLine;

public class DependencyInfoMixin {
	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	Map<String, String> properties;
	@CommandLine.Option(names = {
			"--deps" }, converter = CommaSeparatedConverter.class, description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;
	@CommandLine.Option(names = { "--repos" }, description = "Add additional repositories.")
	List<String> repositories;
	@CommandLine.Option(names = { "--cp", "--class-path" }, description = "Add class path entries.")
	List<String> classpaths;

	public List<String> getDependencies() {
		return dependencies;
	}

	public List<String> getRepositories() {
		return repositories;
	}

	public List<String> getClasspaths() {
		return classpaths;
	}

	public Map<String, String> getProperties() {
		return properties;
	}
}
