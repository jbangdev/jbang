package dev.jbang.cli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.catalog.CatalogRef;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.ConsoleOutput;
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
				Path hiddenCatalog = catalogFile.resolve(Settings.JBANG_DOT_DIR)
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
		Map<String, CatalogRef> cats = dev.jbang.catalog.Catalog.getMerged(true).catalogs;
		cats
			.entrySet()
			.stream()
			.forEach(e -> {
				String ref = e.getValue().catalogRef;
				err.println("Updating catalog '" + e.getKey() + "' from " + ref + "...");
				Util.freshly(() -> {
					try {
						dev.jbang.catalog.Catalog.getByRef(ref);
					} catch (Exception ex) {
						Util.warnMsg("Unable to read catalog " + ref + " (referenced from "
								+ e.getValue().catalog.catalogRef + ")");
					}
				});
			});
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "list", description = "Show currently defined catalogs.")
class CatalogList extends BaseCatalogCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the catalog")
	boolean showOrigin;

	@CommandLine.Option(names = { "--json" }, description = "Output as JSON")
	boolean json;

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
			if (showOrigin) {
				printCatalogsWithOrigin(out, name, catalog, json);
			} else {
				printCatalogs(out, name, catalog, json);
			}
		} else {
			dev.jbang.catalog.Catalog catalog = dev.jbang.catalog.Catalog.getByName(name);
			if (json) {
				List<CatalogOut> aliasCats = AliasList.getAliasesWithOrigin(name, catalog);
				List<CatalogOut> tplCats = TemplateList.getTemplatesWithOrigin(name, catalog, false, false);
				List<CatalogOut> catCats = getCatalogsWithOrigin(name, catalog);
				Map<String, CatalogOut> cats = new HashMap<>();
				for (CatalogOut cat : aliasCats) {
					cats.put(cat.resourceRef, cat);
				}
				for (CatalogOut cat : tplCats) {
					if (cats.containsKey(cat.resourceRef)) {
						cats.get(cat.resourceRef).templates = cat.templates;
					} else {
						cats.put(cat.resourceRef, cat);
					}
				}
				for (CatalogOut cat : catCats) {
					if (cats.containsKey(cat.resourceRef)) {
						cats.get(cat.resourceRef).catalogs = cat.catalogs;
					} else {
						cats.put(cat.resourceRef, cat);
					}
				}
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(cats.values(), out);
			} else {
				if (!catalog.aliases.isEmpty()) {
					out.println("Aliases:");
					out.println("--------");
					AliasList.printAliases(out, name, catalog, false);
				}
				if (!catalog.templates.isEmpty()) {
					out.println("Templates:");
					out.println("----------");
					TemplateList.printTemplates(out, name, catalog, false, false, false);
				}
				if (!catalog.catalogs.isEmpty()) {
					out.println("Catalogs:");
					out.println("---------");
					printCatalogs(out, name, catalog, false);
				}
			}
		}
		return EXIT_OK;
	}

	static void printCatalogs(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog, boolean json) {
		List<CatalogRefOut> catalogs = catalog.catalogs
														.keySet()
														.stream()
														.sorted()
														.map(name -> getCatalogRefOut(catalogName, catalog, name))
														.collect(Collectors.toList());

		if (json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(catalogs, out);
		} else {
			catalogs.forEach(c -> printCatalogRef(out, c, 0));
		}
	}

	static List<CatalogOut> getCatalogsWithOrigin(String catalogName, dev.jbang.catalog.Catalog catalog) {
		Map<ResourceRef, List<CatalogRefOut>> groups = catalog.catalogs
																		.keySet()
																		.stream()
																		.sorted()
																		.map(name -> getCatalogRefOut(catalogName,
																				catalog, name))
																		.collect(Collectors.groupingBy(
																				c -> c._catalogRef));
		return groups	.entrySet()
						.stream()
						.map(e -> new CatalogOut(null, e.getKey(),
								null, null, e.getValue()))
						.collect(Collectors.toList());
	}

	static void printCatalogsWithOrigin(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog,
			boolean json) {
		List<CatalogOut> catalogs = getCatalogsWithOrigin(catalogName, catalog);
		if (json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(catalogs, out);
		} else {
			catalogs.forEach(cat -> {
				out.println(ConsoleOutput.bold(cat.resourceRef));
				cat.catalogs.forEach(c -> printCatalogRef(out, c, 3));
			});
		}
	}

	static class CatalogOut {
		public String name;
		public String resourceRef;
		public String backingResource;
		public String description;
		public List<AliasList.AliasOut> aliases;
		public List<TemplateList.TemplateOut> templates;
		public List<CatalogRefOut> catalogs;

		public CatalogOut(String name, ResourceRef ref, List<AliasList.AliasOut> aliases,
				List<TemplateList.TemplateOut> templates, List<CatalogRefOut> catalogs) {
			this.name = name;
			if (name == null) {
				if (aliases != null) {
					this.name = aliases.get(0).catalogName;
				}
				if (templates != null) {
					this.name = templates.get(0).catalogName;
				}
				if (catalogs != null) {
					this.name = catalogs.get(0).catalogName;
				}
			}
			resourceRef = ref.getOriginalResource();
			backingResource = ref.getFile().toString();
			this.aliases = aliases;
			this.templates = templates;
			this.catalogs = catalogs;
		}
	}

	static class CatalogRefOut {
		public String name;
		public String catalogName;
		public String fullName;
		public String catalogRef;
		public String description;
		public transient ResourceRef _catalogRef;
	}

	private static CatalogRefOut getCatalogRefOut(String catalogName, dev.jbang.catalog.Catalog catalog, String name) {
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		CatalogRef ref = catalog.catalogs.get(name);

		CatalogRefOut out = new CatalogRefOut();
		out.name = name;
		out.catalogName = catalogName;
		out.fullName = fullName;
		out.catalogRef = ref.catalogRef;
		out.description = ref.description;
		out._catalogRef = ref.catalog.catalogRef;
		return out;
	}

	private static void printCatalogRef(PrintStream out, CatalogRefOut catalogRef, int indent) {
		out.print(Util.repeat(" ", indent));
		if (catalogRef.description != null) {
			out.println(ConsoleOutput.yellow(catalogRef.fullName) + " = " + catalogRef.description);
			out.println(Util.repeat(" ", catalogRef.fullName.length() + indent) + "   ("
					+ catalogRef.catalogRef + ")");
		} else {
			out.println(ConsoleOutput.yellow(catalogRef.fullName) + " = " + catalogRef.catalogRef);
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
