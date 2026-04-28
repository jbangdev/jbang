package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;

public class BuildMixin {

	@Option(shortName = 'j', name = "java", description = "JDK version to use for running the script.")
	public String javaVersion;

	@Option(shortName = 'm', name = "main", description = "Main class to use when running. Used primarily for running jar's. Can be a glob pattern using ? and *.")
	public String main;

	@Option(name = "module", parser = StrictOptionParser.class, description = "Treat resource as a module. Optionally with the given module name")
	public String module;

	@OptionList(shortName = 'C', name = "compile-option", description = "Options to pass to the compiler")
	public List<String> compileOptions;

	@OptionGroup(name = "manifest")
	public Map<String, String> manifestOptions;

	@Option(name = "integrations", hasValue = false, negatable = true, description = "Enable or disable integration execution (default: true)")
	Boolean integrations;

	public Boolean getIntegrations() {
		return integrations;
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
		if (Boolean.TRUE.equals(getIntegrations())) {
			opts.add("--integrations");
		} else if (Boolean.FALSE.equals(getIntegrations())) {
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
		return opts;
	}
}
