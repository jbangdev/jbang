package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.catalog.CatalogRef;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

@CommandDefinition(name = "catalog", description = "Manage Catalogs of aliases.", groupCommands = {
		Catalog.CatalogAdd.class, Catalog.CatalogUpdate.class,
		Catalog.CatalogList.class,
		Catalog.CatalogRemove.class }, generateHelp = true, helpGroup = "Configuration")
public class Catalog extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	static abstract class BaseCatalogCommand extends BaseCommand {

		@Mixin
		CatalogFileOptionsMixin catalogOptions;
	}

	@CommandDefinition(name = "add", description = "Add a catalog.", generateHelp = true)
	public static class CatalogAdd extends BaseCatalogCommand {

		@Option(shortName = 'd', name = "description", description = "A description for the catalog")
		String description;

		@Option(name = "name", description = "A name for the catalog")
		String name;

		@Option(name = "force", hasValue = false, description = "Force overwriting of existing catalog")
		boolean force;

		@Option(name = "import", description = "Import catalog items into the catalog's scope")
		Boolean importItems;

		@Argument(paramLabel = "fileOrURL", description = "A file or URL to a catalog file", required = true)
		String urlOrFile;

		@Override
		public Integer doCall() throws IOException {
			if (name != null && !dev.jbang.catalog.Catalog.isValidName(name)) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"Invalid catalog name, it should start with a letter followed by 0 or more letters, digits, underscores, hyphens or dots");
			}
			if (name == null) {
				name = CatalogUtil.nameFromRef(urlOrFile);
			}
			CatalogRef ref = CatalogRef.get(urlOrFile);
			Path catFile = catalogOptions.getCatalogOrDefault();
			if (force || !CatalogUtil.hasCatalogRef(catFile, name)) {
				CatalogUtil.addCatalogRef(catFile, name, ref.catalogRef, ref.description, importItems);
			} else {
				Util.infoMsg("A catalog with name '" + name + "' already exists, use '--force' to add anyway.");
				return EXIT_INVALID_INPUT;
			}
			info(String.format("Catalog '%s' added to '%s'", name, catFile));
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "update", description = "Retrieve the latest contents of the catalogs.", generateHelp = true)
	public static class CatalogUpdate extends BaseCatalogCommand {

		@Override
		public Integer doCall() throws IOException {
			Map<String, CatalogRef> cats = dev.jbang.catalog.Catalog.getMerged(false, true).catalogs;
			cats
				.entrySet()
				.stream()
				.forEach(e -> {
					String ref = e.getValue().catalogRef;
					System.err.println("Updating catalog '" + e.getKey() + "' from " + ref + "...");
					Util.freshly(() -> {
						try {
							dev.jbang.catalog.Catalog.getByRef(ref);
						} catch (Exception ex) {
							Util.warnMsg("Unable to read catalog " + ref + " (referenced from "
									+ e.getValue().catalog.catalogRef + ")");
						}
						return null;
					});
				});
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "list", description = "Show currently defined catalogs.", generateHelp = true)
	public static class CatalogList extends BaseCatalogCommand {

		@Option(name = "show-origin", hasValue = false, description = "Show the origin of the catalog")
		boolean showOrigin;

		@Argument(paramLabel = "name", description = "The name of a catalog")
		String name;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		private static final int INDENT_SIZE = 3;

		@Override
		public Integer doCall() throws IOException {
			PrintStream out = System.out;
			if (name == null) {
				dev.jbang.catalog.Catalog catalog;
				Path cat = catalogOptions.getCatalog(true);
				if (cat != null) {
					catalog = dev.jbang.catalog.Catalog.get(cat);
				} else {
					catalog = dev.jbang.catalog.Catalog.getMerged(true, false);
				}
				if (showOrigin) {
					printCatalogsWithOrigin(out, name, catalog, format);
				} else {
					printCatalogs(out, name, catalog, format);
				}
			} else {
				dev.jbang.catalog.Catalog catalog = dev.jbang.catalog.Catalog.getByName(name);
				if (format == OutputFormat.json) {
					List<CatalogOut> aliasCats = Alias.AliasList.getAliasesWithOrigin(name, catalog);
					List<CatalogOut> tplCats = Template.TemplateList.getTemplatesWithOrigin(name, catalog, false,
							false);
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
						Alias.AliasList.printAliases(out, name, catalog, OutputFormat.text);
					}
					if (!catalog.templates.isEmpty()) {
						out.println("Templates:");
						out.println("----------");
						Template.TemplateList.printTemplates(out, name, catalog, false, false, OutputFormat.text);
					}
					if (!catalog.catalogs.isEmpty()) {
						out.println("Catalogs:");
						out.println("---------");
						printCatalogs(out, name, catalog, OutputFormat.text);
					}
				}
			}
			return EXIT_OK;
		}

		static void printCatalogs(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog,
				OutputFormat format) {
			List<CatalogRefOut> catalogs = catalog.catalogs
				.keySet()
				.stream()
				.sorted()
				.map(name -> getCatalogRefOut(catalogName, catalog, name))
				.collect(Collectors.toList());

			if (format == OutputFormat.json) {
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
			return groups.entrySet()
				.stream()
				.map(e -> new CatalogOut(null, e.getKey(),
						null, null, e.getValue()))
				.collect(Collectors.toList());
		}

		static void printCatalogsWithOrigin(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog,
				OutputFormat format) {
			List<CatalogOut> catalogs = getCatalogsWithOrigin(catalogName, catalog);
			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(catalogs, out);
			} else {
				catalogs.forEach(cat -> {
					out.println(ConsoleOutput.bold(dev.jbang.catalog.Catalog.simplifyRef(cat.resourceRef)));
					cat.catalogs.forEach(c -> printCatalogRef(out, c, 1));
				});
			}
		}

		static class CatalogOut {
			public String name;
			public String resourceRef;
			public String description;
			public List<Alias.AliasList.AliasOut> aliases;
			public List<Template.TemplateList.TemplateOut> templates;
			public List<CatalogRefOut> catalogs;

			public CatalogOut(String name, ResourceRef ref, List<Alias.AliasList.AliasOut> aliases,
					List<Template.TemplateList.TemplateOut> templates, List<CatalogRefOut> catalogs) {
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
			public boolean importItems;

			public transient ResourceRef _catalogRef;
		}

		private static CatalogRefOut getCatalogRefOut(String catalogName, dev.jbang.catalog.Catalog catalog,
				String name) {
			CatalogRef ref = catalog.catalogs.get(name);
			String catName = catalogName != null ? dev.jbang.catalog.Catalog.simplifyRef(catalogName)
					: CatalogUtil.catalogRef(name);
			String fullName = catalogName != null ? name + "@" + catName : name;

			CatalogRefOut out = new CatalogRefOut();
			out.name = name;
			out.catalogName = catName;
			out.fullName = fullName;
			out.catalogRef = ref.catalogRef;
			out.description = ref.description;
			out.importItems = Boolean.TRUE.equals(ref.importItems);
			out._catalogRef = ref.catalog.catalogRef;
			return out;
		}

		private static void printCatalogRef(PrintStream out, CatalogRefOut catalogRef, int indent) {
			String prefix1 = Util.repeat(" ", indent * INDENT_SIZE);
			String prefix2 = Util.repeat(" ", (indent + 1) * INDENT_SIZE);
			out.println(prefix1 + getColoredFullName(catalogRef.fullName)
					+ (catalogRef.importItems ? ConsoleOutput.magenta(" [importing]") : ""));
			if (catalogRef.description != null) {
				out.println(prefix2 + catalogRef.description);
			}
			out.println(prefix2 + catalogRef.catalogRef);
		}

		static String getColoredFullName(String fullName) {
			StringBuilder res = new StringBuilder();
			String[] parts = fullName.split("@");
			res.append(ConsoleOutput.yellow(parts[0]));
			for (int i = 1; i < parts.length; i++) {
				res.append(ConsoleOutput.cyan("@"));
				res.append(parts[i]);
			}
			return res.toString();
		}
	}

	@CommandDefinition(name = "remove", description = "Remove existing catalog.", generateHelp = true)
	public static class CatalogRemove extends BaseCatalogCommand {

		@Argument(paramLabel = "name", description = "The name of the catalog", required = true)
		String name;

		@Override
		public Integer doCall() throws IOException {
			Path cat = catalogOptions.getCatalog(true);
			if (cat != null) {
				CatalogUtil.removeCatalogRef(cat, name);
			} else {
				CatalogUtil.removeNearestCatalogRef(name);
			}
			return EXIT_OK;
		}
	}
}
