package dev.jbang.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases for scripts.", subcommands = { AliasAdd.class,
		AliasList.class, AliasRemove.class })
public class Alias {
}

abstract class BaseAliasCommand extends BaseCommand {

	@CommandLine.Option(names = { "--global", "-g" }, description = "Use the global (user) catalog file")
	boolean global;

	@CommandLine.Option(names = { "--file", "-f" }, description = "Path to the catalog file to use")
	Path catalogFile;

	protected Path getCatalog() {
		if (global) {
			return Settings.getAliasesFile();
		} else {
			return catalogFile;
		}
	}
}

@CommandLine.Command(name = "add", description = "Add alias for script reference.")
class AliasAdd extends BaseAliasCommand {

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the alias")
	String description;

	@CommandLine.Option(names = { "-D" }, description = "set a system property")
	Map<String, String> properties;

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "A name for the alias", arity = "1")
	String name;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "1", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	@CommandLine.Parameters(paramLabel = "params", index = "2..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams;

	@Override
	public Integer doCall() {
		if (!AliasUtil.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid alias name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
		}
		if (getCatalog() != null) {
			AliasUtil.addAlias(getCatalog(), name, scriptOrFile, description, userParams, properties);
		} else {
			AliasUtil.addNearestAlias(null, name, scriptOrFile, description, userParams, properties);
		}
		return CommandLine.ExitCode.OK;
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined aliases or from the given catalog.")
class AliasList extends BaseAliasCommand {

	@CommandLine.Parameters(paramLabel = "catalogName", index = "0", description = "The name of a catalog", arity = "0..1")
	String catalogName;

	@Override
	public Integer doCall() {
		PrintWriter out = spec.commandLine().getOut();
		AliasUtil.Aliases aliases;
		if (catalogName != null) {
			aliases = AliasUtil.getCatalogAliasesByName(catalogName, false);
		} else if (getCatalog() != null) {
			aliases = AliasUtil.getAliasesFromCatalogFile(getCatalog(), false);
		} else {
			aliases = AliasUtil.getAllAliasesFromLocalCatalogs(null);
		}
		printAliases(out, catalogName, aliases);
		return CommandLine.ExitCode.OK;
	}

	static void printAliases(PrintWriter out, String catalogName, AliasUtil.Aliases aliases) {
		aliases.aliases
						.keySet()
						.stream()
						.sorted()
						.forEach(name -> {
							AliasUtil.Alias alias = aliases.aliases.get(name);
							String fullName = catalogName != null ? name + "@" + catalogName : name;
							String scriptRef = alias.scriptRef;
							if (!aliases.aliases.containsKey(scriptRef)
									&& !AliasUtil.isValidCatalogReference(scriptRef)) {
								scriptRef = alias.resolve(null);
							}
							if (alias.description != null) {
								out.println(fullName + " = " + alias.description);
								if (Util.isVerbose())
									out.println(Util.repeat(" ", fullName.length()) + "   (" + scriptRef + ")");
							} else {
								out.println(fullName + " = " + scriptRef);
							}
							if (alias.arguments != null) {
								out.println(
										Util.repeat(" ", fullName.length()) + "   Arguments: "
												+ String.join(" ", alias.arguments));
							}
							if (alias.properties != null) {
								out.println(
										Util.repeat(" ", fullName.length()) + "   Properties: " + alias.properties);
							}
						});
	}
}

@CommandLine.Command(name = "remove", description = "Remove existing alias.")
class AliasRemove extends BaseAliasCommand {

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the alias", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		if (getCatalog() != null) {
			AliasUtil.removeAlias(getCatalog(), name);
		} else {
			AliasUtil.removeNearestAlias(null, name);
		}
		return CommandLine.ExitCode.OK;
	}
}
