package dev.jbang.cli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.Settings;
import dev.jbang.catalog.CatalogRef;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "catalog", description = "Manage Catalogs of aliases.", subcommands = { CatalogAdd.class,
		CatalogUpdate.class, CatalogList.class, CatalogRemove.class })
public class Catalog {
}

abstract class BaseCatalogCommand extends BaseCommand {

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
				Path defaultCatalog = catalogFile.resolve(dev.jbang.catalog.Catalog.JBANG_CATALOG_JSON);
				Path hiddenCatalog = catalogFile.resolve(CatalogUtil.JBANG_DOT_DIR)
												.resolve(dev.jbang.catalog.Catalog.JBANG_CATALOG_JSON);
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

@CommandLine.Command(name = "add", description = "Add a catalog.")
class CatalogAdd extends BaseCatalogCommand {

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the catalog")
	String description;

	@CommandLine.Option(names = { "--name" }, description = "A name for the alias")
	String name;

	@CommandLine.Parameters(paramLabel = "urlOrFile", index = "0", description = "A file or URL to a catalog file", arity = "1")
	String urlOrFile;

	@Override
	public Integer doCall() {
		if (name != null && !dev.jbang.catalog.Catalog.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid catalog name, it should start with a letter followed by 0 or more letters, digits, underscores, hyphens or dots");
		}
		if (name == null) {
			name = CatalogUtil.nameFromRef(urlOrFile);
		}
		CatalogRef ref = CatalogRef.createByRefOrImplicit(urlOrFile);
		Path catFile = getCatalog(false);
		if (catFile != null) {
			CatalogUtil.addCatalogRef(catFile, name, ref.catalogRef, ref.description);
		} else {
			catFile = CatalogUtil.addNearestCatalogRef(name, ref.catalogRef, ref.description);
		}
		info(String.format("Catalog '%s' added to '%s'", name, catFile));
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "update", description = "Retrieve the latest contents of the catalogs.")
class CatalogUpdate extends BaseCatalogCommand {

	@Override
	public Integer doCall() {
		PrintWriter err = spec.commandLine().getErr();
		dev.jbang.catalog.Catalog.getMerged(true).catalogs
															.entrySet()
															.stream()
															.forEach(e -> {
																err.println(
																		"Updating catalog '" + e.getKey()
																				+ "' from "
																				+ e.getValue().catalogRef + "...");
																dev.jbang.catalog.Catalog.getByRef(
																		e.getValue().catalogRef);
															});
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "list", description = "Show currently defined catalogs.")
class CatalogList extends BaseCatalogCommand {

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of a catalog", arity = "0..1")
	String name;

	@Override
	public Integer doCall() {
		PrintStream out = System.out;
		if (name == null) {
			dev.jbang.catalog.Catalog catalog;
			Path cat = getCatalog(true);
			if (cat != null) {
				catalog = dev.jbang.catalog.Catalog.get(cat);
			} else {
				catalog = dev.jbang.catalog.Catalog.getMerged(true);
			}
			printCatalogs(out, name, catalog);
		} else {
			dev.jbang.catalog.Catalog catalog = dev.jbang.catalog.Catalog.getByName(name);
			if (!catalog.aliases.isEmpty()) {
				out.println("Aliases:");
				out.println("--------");
				AliasList.printAliases(out, name, catalog);
			}
			if (!catalog.templates.isEmpty()) {
				out.println("Templates:");
				out.println("----------");
				TemplateList.printTemplates(out, name, catalog, false);
			}
			if (!catalog.catalogs.isEmpty()) {
				out.println("Catalogs:");
				out.println("---------");
				printCatalogs(out, name, catalog);
			}
		}
		return EXIT_OK;
	}

	static void printCatalogs(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog) {
		catalog.catalogs
						.keySet()
						.stream()
						.sorted()
						.forEach(nm -> {
							printCatalog(out, catalogName, catalog, nm);
						});
	}

	private static void printCatalog(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog,
			String name) {
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		CatalogRef ref = catalog.catalogs.get(name);
		if (ref.description != null) {
			out.println(fullName + " = " + ref.description);
			out.println(Util.repeat(" ", fullName.length()) + "   ("
					+ ref.catalogRef
					+ ")");
		} else {
			out.println(fullName + " = " + ref.catalogRef);
		}
	}
}

@CommandLine.Command(name = "remove", description = "Remove existing catalog.")
class CatalogRemove extends BaseCatalogCommand {

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the catalog", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		Path cat = getCatalog(true);
		if (cat != null) {
			CatalogUtil.removeCatalogRef(cat, name);
		} else {
			CatalogUtil.removeNearestCatalogRef(name);
		}
		return EXIT_OK;
	}
}
