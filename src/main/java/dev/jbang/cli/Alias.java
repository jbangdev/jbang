package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.jbang.AliasUtil;
import dev.jbang.ExitException;
import dev.jbang.Script;
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

	protected Path getCatalog(boolean strict) {
		Path cat;
		if (global) {
			cat = Settings.getUserCatalogFile();
		} else {
			if (catalogFile != null && Files.isDirectory(catalogFile)) {
				Path defaultCatalog = catalogFile.resolve(AliasUtil.JBANG_CATALOG_JSON);
				Path hiddenCatalog = catalogFile.resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
				if (!Files.exists(defaultCatalog) && Files.exists(hiddenCatalog)) {
					cat = hiddenCatalog;
				} else {
					cat = defaultCatalog;
				}
			} else {
				cat = catalogFile;
			}
			if (strict && cat != null && !Files.isRegularFile(cat)) {
				throw new IllegalArgumentException("Catalog file not found at: " + catalogFile);
			}
		}
		return cat;
	}
}

@CommandLine.Command(name = "add", description = "Add alias for script reference.")
class AliasAdd extends BaseAliasCommand {

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the alias")
	String description;

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	Map<String, String> properties;

	@CommandLine.Option(names = { "--name" }, description = "A name for the command")
	String name;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams;

	@Override
	public Integer doCall() {
		if (name != null && !AliasUtil.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid alias name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
		}
		try {
			Script script = BaseScriptCommand.prepareScript(scriptOrFile);
			if (name == null) {
				name = AppInstall.chooseCommandName(script);
			}
			Path catFile = getCatalog(false);
			if (catFile != null) {
				AliasUtil.addAlias(null, catFile, name, scriptOrFile, description, userParams, properties);
			} else {
				catFile = AliasUtil.addNearestAlias(null, name, scriptOrFile, description, userParams, properties);
			}
			info(String.format("Alias '%s' added to '%s'", name, catFile));
		} catch (IOException e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Could not add alias", e);
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
		AliasUtil.Catalog catalog;
		Path cat = getCatalog(true);
		if (catalogName != null) {
			catalog = AliasUtil.getCatalogByName(null, catalogName, false);
		} else if (cat != null) {
			catalog = AliasUtil.getCatalog(cat, false);
		} else {
			catalog = AliasUtil.getMergedCatalog(null, true);
		}
		if (showOrigin) {
			printAliasesWithOrigin(out, catalogName, catalog);
		} else {
			printAliases(out, catalogName, catalog);
		}
		return EXIT_OK;
	}

	static void printAliases(PrintStream out, String catalogName, AliasUtil.Catalog catalog) {
		catalog.aliases
						.keySet()
						.stream()
						.sorted()
						.forEach(name -> {
							printAlias(out, catalogName, catalog, name, 0);
						});
	}

	static void printAliasesWithOrigin(PrintStream out, String catalogName, AliasUtil.Catalog catalog) {
		Map<Path, List<Map.Entry<String, AliasUtil.Alias>>> groups = catalog.aliases
																					.entrySet()
																					.stream()
																					.collect(Collectors.groupingBy(
																							e -> e.getValue().catalog.catalogFile));
		groups.forEach((p, entries) -> {
			out.println(p);
			entries.stream().map(Map.Entry::getKey).sorted().forEach(k -> {
				printAlias(out, catalogName, catalog, k, 3);
			});
		});
	}

	private static void printAlias(PrintStream out, String catalogName, AliasUtil.Catalog catalog, String name,
			int indent) {
		AliasUtil.Alias alias = catalog.aliases.get(name);
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		String scriptRef = alias.scriptRef;
		if (!catalog.aliases.containsKey(scriptRef)
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
		final Path cat = getCatalog(true);
		if (cat != null) {
			AliasUtil.removeAlias(cat, name);
		} else {
			AliasUtil.removeNearestAlias(null, name);
		}
		return EXIT_OK;
	}
}
