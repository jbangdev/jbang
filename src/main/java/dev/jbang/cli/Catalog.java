package dev.jbang.cli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;
import dev.jbang.Util;

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

@CommandLine.Command(name = "add", description = "Add a catalog.")
class CatalogAdd extends BaseCatalogCommand {

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the catalog")
	String description;

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "A name for the catalog", arity = "1")
	String name;

	@CommandLine.Parameters(paramLabel = "urlOrFile", index = "1", description = "A file or URL to a catalog file", arity = "1")
	String urlOrFile;

	@Override
	public Integer doCall() {
		if (!name.matches("^[a-zA-Z][-.\\w]*$")) {
			throw new IllegalArgumentException(
					"Invalid catalog name, it should start with a letter followed by 0 or more letters, digits, underscores, hyphens or dots");
		}
		AliasUtil.CatalogRef ref = AliasUtil.getCatalogRefByRefOrImplicit(urlOrFile, true);
		Path catFile = getCatalog(false);
		if (catFile != null) {
			AliasUtil.addCatalog(null, catFile, name, ref.catalogRef, ref.description);
		} else {
			catFile = AliasUtil.addNearestCatalog(null, name, ref.catalogRef, ref.description);
		}
		info(String.format("Catalog added to %s", catFile));
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "update", description = "Retrieve the latest contents of the catalogs.")
class CatalogUpdate extends BaseCatalogCommand {

	@Override
	public Integer doCall() {
		PrintWriter err = spec.commandLine().getErr();
		AliasUtil.getMergedCatalog(null, true).catalogs
														.entrySet()
														.stream()
														.forEach(e -> {
															err.println(
																	"Updating catalog '" + e.getKey() + "' from "
																			+ e.getValue().catalogRef + "...");
															AliasUtil.getCatalogByRef(e.getValue().catalogRef,
																	true);
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
			AliasUtil.Catalog catalog;
			Path cat = getCatalog(true);
			if (cat != null) {
				catalog = AliasUtil.getCatalog(cat, false);
			} else {
				catalog = AliasUtil.getMergedCatalog(null, true);
			}
			catalog.catalogs
							.keySet()
							.stream()
							.sorted()
							.forEach(nm -> {
								AliasUtil.CatalogRef ref = catalog.catalogs.get(
										nm);
								if (ref.description != null) {
									out.println(nm + " = " + ref.description);
									out.println(Util.repeat(" ", nm.length()) + "   ("
											+ ref.catalogRef
											+ ")");
								} else {
									out.println(nm + " = " + ref.catalogRef);
								}
							});
		} else {
			AliasUtil.Catalog catalog = AliasUtil.getCatalogByName(null, name, false);
			AliasList.printAliases(out, name, catalog);
		}
		return EXIT_OK;
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
			AliasUtil.removeCatalog(cat, name);
		} else {
			AliasUtil.removeNearestCatalog(null, name);
		}
		return EXIT_OK;
	}
}
