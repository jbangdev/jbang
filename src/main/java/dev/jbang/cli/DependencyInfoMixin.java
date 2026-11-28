package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;

import dev.jbang.util.Util;

public class DependencyInfoMixin {

	@OptionGroup(shortName = 'D', helpGroup = "Runtime options", description = "set a system property", defaultValue = "true")
	Map<String, String> properties;

	@OptionList(name = "deps", valueSeparator = ',', helpGroup = "Dependency options", description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;

	@OptionList(name = "repos", valueSeparator = ',', helpGroup = "Dependency options", description = "Add additional repositories.")
	List<String> repositories;

	@OptionList(name = "cp", aliases = {
			"class-path" }, helpGroup = "Dependency options", description = "Add class path entries.")
	List<String> classpaths;

	@Option(name = "ignore-transitive-repositories", aliases = {
			"itr" }, hasValue = false, helpGroup = "Dependency options", description = "Ignore remote repositories found in transitive dependencies")
	boolean ignoreTransitiveRepositories;

	public Map<String, String> getProperties() {
		return properties != null && properties.isEmpty() ? null : properties;
	}

	public List<String> getDependencies() {
		return dependencies;
	}

	public List<String> getRepositories() {
		return repositories;
	}

	public List<String> getClasspaths() {
		return classpaths;
	}

	public void applyIgnoreTransitiveRepositories() {
		if (ignoreTransitiveRepositories) {
			Util.setIgnoreTransitiveRepositories(true);
		}
	}

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (properties != null) {
			for (Map.Entry<String, String> e : properties.entrySet()) {
				opts.add("-D" + e.getKey() + "=" + e.getValue());
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
