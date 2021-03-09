package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

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
				Path defaultCatalog = catalogFile.resolve(Catalog.JBANG_CATALOG_JSON);
				Path hiddenCatalog = catalogFile.resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
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

	@CommandLine.Option(names = { "--name" }, description = "A name for the alias")
	String name;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams;

	@Override
	public Integer doCall() {
		if (name != null && !Catalog.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid alias name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
		}

		RunContext ctx = RunContext.empty();
		Source src = Source.forResource(scriptOrFile, ctx);
		if (name == null) {
			name = CatalogUtil.nameFromRef(ctx.getOriginalRef());
		}

		String desc = description != null ? description : src.getDescription().orElse(null);

		Path catFile = getCatalog(false);
		if (catFile != null) {
			CatalogUtil.addAlias(catFile, name, scriptOrFile, desc, userParams, properties);
		} else {
			catFile = CatalogUtil.addNearestAlias(name, scriptOrFile, desc, userParams, properties);
		}
		info(String.format("Alias '%s' added to '%s'", name, catFile));
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
		Catalog catalog;
		Path cat = getCatalog(true);
		if (catalogName != null) {
			catalog = Catalog.getByName(catalogName);
		} else if (cat != null) {
			catalog = Catalog.get(cat);
		} else {
			catalog = Catalog.getMerged(true);
		}
		if (showOrigin) {
			printAliasesWithOrigin(out, catalogName, catalog);
		} else {
			printAliases(out, catalogName, catalog);
		}
		return EXIT_OK;
	}

	static void printAliases(PrintStream out, String catalogName, Catalog catalog) {
		catalog.aliases
						.keySet()
						.stream()
						.sorted()
						.forEach(name -> printAlias(out, catalogName, catalog, name, 0));
	}

	static void printAliasesWithOrigin(PrintStream out, String catalogName, Catalog catalog) {
		Map<Path, List<Map.Entry<String, dev.jbang.catalog.Alias>>> groups = catalog.aliases
																							.entrySet()
																							.stream()
																							.collect(
																									Collectors.groupingBy(
																											e -> e.getValue().catalog.catalogFile));
		groups.forEach((p, entries) -> {
			out.println(p);
			entries.stream().map(Map.Entry::getKey).sorted().forEach(k -> printAlias(out, catalogName, catalog, k, 3));
		});
	}

	private static void printAlias(PrintStream out, String catalogName, Catalog catalog, String name,
			int indent) {
		dev.jbang.catalog.Alias alias = catalog.aliases.get(name);
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		String scriptRef = alias.scriptRef;
		if (!catalog.aliases.containsKey(scriptRef)
				&& !Catalog.isValidCatalogReference(scriptRef)) {
			scriptRef = alias.resolve();
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
			CatalogUtil.removeAlias(cat, name);
		} else {
			CatalogUtil.removeNearestAlias(name);
		}
		return EXIT_OK;
	}
}
