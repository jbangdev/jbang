package dev.jbang.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import dev.jbang.Settings;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases.")
public class Alias {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "add", description = "Add alias for script reference.")
	public Integer add(
			@CommandLine.Option(names = { "--description",
					"-d" }, description = "A description for the alias") String description,
			@CommandLine.Option(names = { "-D" }, description = "set a system property") Map<String, String> properties,
			@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1") String name,
			@CommandLine.Parameters(index = "1", description = "A file or URL to a Java code file", arity = "1") String scriptOrFile,
			@CommandLine.Parameters(index = "2..*", arity = "0..*", description = "Parameters to pass on to the script") List<String> userParams) {

		Settings.addAlias(name, scriptOrFile, description, userParams, properties);
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "list", description = "Show currently defined aliases.")
	public Integer list() {
		Settings.getAliases()
				.keySet()
				.stream()
				.sorted()
				.forEach(a -> {
					PrintWriter out = spec.commandLine().getOut();
					Settings.Alias ai = Settings.getAliases().get(a);
					if (ai.description != null) {
						out.println(a + " = " + ai.description);
						out.println(Util.repeat(" ", a.length()) + "   (" + ai.scriptRef + ")");
					} else {
						out.println(a + " = " + ai.scriptRef);
					}
					if (ai.arguments != null) {
						out.println(Util.repeat(" ", a.length()) + "   Arguments: " + String.join(" ", ai.arguments));
					}
					if (ai.properties != null) {
						out.println(Util.repeat(" ", a.length()) + "   Properties: " + ai.properties);
					}
				});
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "remove", description = "Remove existing alias.")
	public Integer remove(
			@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1") String name) {
		Settings.removeAlias(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
