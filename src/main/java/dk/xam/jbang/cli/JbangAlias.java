package dk.xam.jbang.cli;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases.", subcommands = { JbangAliasAdd.class,
		JbangAliasList.class,
		JbangAliasRemove.class })
public class JbangAlias {
}

@CommandLine.Command(name = "add", description = "Add alias for script reference.")
class JbangAliasAdd extends JbangBaseCommand {

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
class JbangAliasList extends JbangBaseCommand {

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
class JbangAliasRemove extends JbangBaseCommand {

	@CommandLine.Parameters(index = "0", description = "A name for the alias", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		Settings.removeAlias(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
