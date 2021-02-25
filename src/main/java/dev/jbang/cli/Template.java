package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "template", description = "Manage templates for scripts.", subcommands = {
		TemplateAdd.class,
		TemplateList.class, TemplateRemove.class })
public class Template {
}

abstract class BaseTemplateCommand extends BaseCommand {

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

@CommandLine.Command(name = "add", description = "Add template for script reference.")
class TemplateAdd extends BaseTemplateCommand {

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the template")
	String description;

	@CommandLine.Option(names = { "--name" }, description = "A name for the template")
	String name;

	@CommandLine.Parameters(paramLabel = "files", index = "0..*", arity = "1..*", description = "Paths or URLs to template files")
	List<String> fileRefs;

	@Override
	public Integer doCall() {
		if (name != null && !Catalog.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid template name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
		}

		// Turn list of files into the map that's needed to store in the Catalog
		Map<String, String> fileRefsMap = new HashMap<>();
		for (String fileRef : fileRefs) {
			String[] ref = fileRef.split("=", 2);
			String target;
			String source;
			if (ref.length == 2) {
				source = ref[1];
				target = ref[0];
				Path t = Paths.get(target).normalize();
				if (t.isAbsolute()) {
					throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
							"Target name may not be absolute: '" + target + "'");
				}
				if (t.normalize().startsWith("..")) {
					throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
							"Target may not refer to parent folders: '" + target + "'");
				}
			} else {
				source = ref[0];
				Path t = Paths.get(source).normalize();
				if (t.isAbsolute() || t.normalize().startsWith("..")) {
					target = t.getFileName().toString();
				} else {
					target = source;
				}
			}
			fileRefsMap.put(target, source);
		}

		// Check that files/URLs exist
		for (String fileRef : fileRefsMap.values()) {
			ResourceRef resourceRef = ResourceRef.forResource(fileRef);
			if (resourceRef == null || !resourceRef.getFile().canRead()) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"File could not be found or read: '" + fileRef + "'");
			}
		}
		if (name == null) {
			name = CatalogUtil.nameFromRef(fileRefs.get(0));
		}

		Path catFile = getCatalog(false);
		if (catFile != null) {
			CatalogUtil.addTemplate(null, catFile, name, fileRefsMap, description);
		} else {
			catFile = CatalogUtil.addNearestTemplate(null, name, fileRefsMap, description);
		}
		info(String.format("Template '%s' added to '%s'", name, catFile));
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined templates or from the given catalog.")
class TemplateList extends BaseTemplateCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the template")
	boolean showOrigin;

	@CommandLine.Parameters(paramLabel = "catalogName", index = "0", description = "The name of a catalog", arity = "0..1")
	String catalogName;

	@Override
	public Integer doCall() {
		PrintStream out = System.out;
		Catalog catalog;
		Path cat = getCatalog(true);
		if (catalogName != null) {
			catalog = Catalog.getByName(null, catalogName);
		} else if (cat != null) {
			catalog = Catalog.get(cat);
		} else {
			catalog = Catalog.getMerged(null, true);
		}
		if (showOrigin) {
			printTemplatesWithOrigin(out, catalogName, catalog);
		} else {
			printTemplates(out, catalogName, catalog);
		}
		return EXIT_OK;
	}

	static void printTemplates(PrintStream out, String catalogName, Catalog catalog) {
		catalog.templates
							.keySet()
							.stream()
							.sorted()
							.forEach(name -> printTemplate(out, catalogName, catalog, name, 0));
	}

	static void printTemplatesWithOrigin(PrintStream out, String catalogName, Catalog catalog) {
		Map<Path, List<Map.Entry<String, dev.jbang.catalog.Template>>> groups = catalog.templates
																									.entrySet()
																									.stream()
																									.collect(
																											Collectors.groupingBy(
																													e -> e.getValue().catalog.catalogFile));
		groups.forEach((p, entries) -> {
			out.println(p);
			entries	.stream()
					.map(Map.Entry::getKey)
					.sorted()
					.forEach(k -> printTemplate(out, catalogName, catalog, k, 3));
		});
	}

	private static void printTemplate(PrintStream out, String catalogName, Catalog catalog, String name,
			int indent) {
		dev.jbang.catalog.Template template = catalog.templates.get(name);
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		out.print(Util.repeat(" ", indent));
		if (template.description != null) {
			out.println(yellow(fullName) + " = " + template.description);
		} else {
			out.println(yellow(fullName) + " = ");
		}
		for (String dest : template.fileRefs.keySet()) {
			String ref = template.fileRefs.get(dest);
			if (ref == null || ref.isEmpty()) {
				ref = dest;
			}
			ref = template.resolve(null, ref);
			if (ref.equals(dest)) {
				out.println("   " + ref);
			} else {
				out.println("   " + dest + " (from " + ref + ")");
			}
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

@CommandLine.Command(name = "remove", description = "Remove existing template.")
class TemplateRemove extends BaseTemplateCommand {

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the template", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		final Path cat = getCatalog(true);
		if (cat != null) {
			CatalogUtil.removeTemplate(cat, name);
		} else {
			CatalogUtil.removeNearestTemplate(null, name);
		}
		return EXIT_OK;
	}
}
