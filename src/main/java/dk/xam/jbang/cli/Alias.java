package dk.xam.jbang.cli;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases.", subcommands = { AliasAdd.class,
		AliasList.class,
		AliasRemove.class })
public class Alias {
}

@CommandLine.Command(name = "add", description = "Add alias for script reference.")
class AliasAdd extends BaseCommand {

	@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1")
	String name;

	@CommandLine.Parameters(index = "1", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	@Override
	public Integer doCall() {
		Settings.addAlias(name, scriptOrFile);
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@CommandLine.Command(name = "list", description = "Show currently defined aliases.")
class AliasList extends BaseCommand {

	@Override
	public Integer doCall() {
		Settings.getAliases()
				.keySet()
				.stream()
				.sorted()
				.forEach(a -> spec.commandLine().getOut().println(a + " = " + Settings.getAliases().get(a).scriptRef));
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@CommandLine.Command(name = "remove", description = "Remove existing alias.")
class AliasRemove extends BaseCommand {

	@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		Settings.removeAlias(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
