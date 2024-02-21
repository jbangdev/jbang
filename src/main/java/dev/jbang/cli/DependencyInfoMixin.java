package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import picocli.CommandLine;

public class DependencyInfoMixin {
	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	Map<String, String> properties;
	@CommandLine.Option(names = {
			"--deps" }, converter = CommaSeparatedConverter.class, description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;
	@CommandLine.Option(names = {
			"--repos" }, converter = CommaSeparatedConverter.class, description = "Add additional repositories.")
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

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (properties != null) {
			for (Map.Entry<String, String> e : properties.entrySet()) {
				opts.add("-D");
				opts.add(e.getKey() + "=" + e.getValue());
			}
		}
		if (dependencies != null) {
			for (String d : dependencies) {
				opts.add("--deps");
				opts.add(d);
			}
		}
		if (repositories != null) {
			for (String r : repositories) {
				opts.add("--repos");
				opts.add(r);
			}
		}
		if (classpaths != null) {
			for (String c : classpaths) {
				opts.add("--cp");
				opts.add(c);
			}
		}
		return opts;
	}
}
