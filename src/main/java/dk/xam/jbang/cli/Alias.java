package dk.xam.jbang.cli;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases.")
public class Alias {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "add", description = "Add alias for script reference.")
	public Integer add(
			@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1") String name,
			@CommandLine.Parameters(index = "1", description = "A file or URL to a Java code file", arity = "1") String scriptOrFile) {
		Settings.addAlias(name, scriptOrFile);
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "list", description = "Show currently defined aliases.")
	public Integer list() {
		Settings.getAliases()
				.keySet()
				.stream()
				.sorted()
				.forEach(a -> spec.commandLine().getOut().println(a + " = " + Settings.getAliases().get(a).scriptRef));
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "remove", description = "Remove existing alias.")
	public Integer remove(
			@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1") String name) {
		Settings.removeAlias(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
