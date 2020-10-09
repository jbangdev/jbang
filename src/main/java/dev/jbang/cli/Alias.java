package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
			if (Files.isDirectory(catalogFile)) {
				catalogFile = Paths.get(catalogFile.toString(), AliasUtil.JBANG_CATALOG_JSON);
			}
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
			AliasUtil.addAlias(null, getCatalog(), name, scriptOrFile, description, userParams, properties);
		} else {
			AliasUtil.addNearestAlias(null, name, scriptOrFile, description, userParams, properties);
		}
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined aliases or from the given catalog.")
class AliasList extends BaseAliasCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the alias")
	boolean showOrigin;

	@CommandLine.Parameters(paramLabel = "catalogName", index = "0", description = "The name of a catalog", arity = "0..1")
	String catalogName;

	@Override
	public Integer doCall() {
		PrintStream out = System.out;
		AliasUtil.Aliases aliases;
		if (catalogName != null) {
			aliases = AliasUtil.getCatalogAliasesByName(catalogName, false);
		} else if (getCatalog() != null) {
			aliases = AliasUtil.getAliasesFromCatalogFile(getCatalog(), false);
		} else {
			aliases = AliasUtil.getAllAliasesFromLocalCatalogs(null);
		}
		if (showOrigin) {
			printAliasesWithOrigin(out, catalogName, aliases);
		} else {
			printAliases(out, catalogName, aliases);
		}
		return EXIT_OK;
	}

	static void printAliases(PrintStream out, String catalogName, AliasUtil.Aliases aliases) {
		aliases.aliases
						.keySet()
						.stream()
						.sorted()
						.forEach(name -> {
							printAlias(out, catalogName, aliases, name, 0);
						});
	}

	static void printAliasesWithOrigin(PrintStream out, String catalogName, AliasUtil.Aliases aliases) {
		Map<Path, List<Map.Entry<String, AliasUtil.Alias>>> groups = aliases.aliases
																					.entrySet()
																					.stream()
																					.collect(Collectors.groupingBy(
																							e -> e.getValue().aliases.catalogFile));
		groups.forEach((p, entries) -> {
			out.println(p);
			entries.stream().map(Map.Entry::getKey).sorted().forEach(k -> {
				printAlias(out, catalogName, aliases, k, 3);
			});
		});
	}

	private static void printAlias(PrintStream out, String catalogName, AliasUtil.Aliases aliases, String name,
			int indent) {
		AliasUtil.Alias alias = aliases.aliases.get(name);
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		String scriptRef = alias.scriptRef;
		if (!aliases.aliases.containsKey(scriptRef)
				&& !AliasUtil.isValidCatalogReference(scriptRef)) {
			scriptRef = alias.resolve(null);
		}
		out.print(Util.repeat(" ", indent));
		if (alias.description != null) {
			out.println(yellow(fullName) + " = " + alias.description);
			if (Util.isVerbose())
				out.println(Util.repeat(" ", fullName.length() + indent) + faint("   (" + scriptRef + ")"));
		} else {
			out.println(yellow(fullName) + " = " + scriptRef);
		}
		if (alias.arguments != null) {
			out.println(
					Util.repeat(" ", fullName.length() + indent) + cyan("   Arguments: ")
							+ String.join(" ", alias.arguments));
		}
		if (alias.properties != null) {
			out.println(
					Util.repeat(" ", fullName.length() + indent) + magenta("   Properties: ") + alias.properties);
		}
	}

	private static String yellow(String text) {
		return CommandLine.Help.Ansi.AUTO.new Text("@|fg(yellow) " + text + "|@").toString();
	}

	private static String cyan(String text) {
		return CommandLine.Help.Ansi.AUTO.new Text("@|fg(cyan) " + text + "|@").toString();
	}

	private static String magenta(String text) {
		return CommandLine.Help.Ansi.AUTO.new Text("@|fg(magenta) " + text + "|@").toString();
	}

	private static String faint(String text) {
		return CommandLine.Help.Ansi.AUTO.new Text("@|faint " + text + "|@").toString();
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
		return EXIT_OK;
	}
}
