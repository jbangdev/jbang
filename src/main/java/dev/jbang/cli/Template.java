package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "template", description = "Manage templates for scripts.", subcommands = {
		TemplateAdd.class, TemplateList.class, TemplateRemove.class })
public class Template {
	public static final Pattern TPL_FILENAME_PATTERN = Pattern.compile("\\{filename}", Pattern.CASE_INSENSITIVE);
	public static final Pattern TPL_BASENAME_PATTERN = Pattern.compile("\\{basename}", Pattern.CASE_INSENSITIVE);
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

		List<Map.Entry<String, String>> splitRefs = fileRefs
															.stream()
															// Turn list of files into a list of target=source pairs
															.map(TemplateAdd::splitFileRef)
															// Check that the source files/URLs exist
															.filter(TemplateAdd::refExists)
															.collect(Collectors.toList());

		// Make sure we have at least a single {basename} or {filename} target
		boolean hasTargetPattern = false;
		for (Map.Entry<String, String> splitRef : splitRefs) {
			String target = splitRef.getKey();
			if (target != null && (Template.TPL_FILENAME_PATTERN.matcher(target).find()
					|| Template.TPL_BASENAME_PATTERN.matcher(target).find())) {
				hasTargetPattern = true;
			}
		}
		if (!hasTargetPattern) {
			// There's no {basename} or {filename} in any of the targets
			Map.Entry<String, String> firstRef = splitRefs.get(0);
			if (firstRef.getKey() == null) {
				String ext = Util.extension(firstRef.getValue());
				String target = ext.isEmpty() ? "{filename}" : "{basename}." + ext;
				splitRefs.set(0, new AbstractMap.SimpleEntry<>(target, firstRef.getValue()));
				warn("No explicit target pattern was set, using first file: " + target + "=" + firstRef.getValue());
			} else {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"A target pattern is required. Prefix at least one of the files with '{filename}=' or '{basename}.ext='");
			}
		}

		Map<String, String> fileRefsMap = new TreeMap<>();
		splitRefs
					.stream()
					// Make sure all file refs have a target
					.map(TemplateAdd::ensureTarget)
					// Create map of file refs
					.forEach(splitRef -> fileRefsMap.put(splitRef.getKey(), splitRef.getValue()));

		if (name == null) {
			name = CatalogUtil.nameFromRef(fileRefs.get(0));
		}

		Path catFile = getCatalog(false);
		if (catFile != null) {
			CatalogUtil.addTemplate(catFile, name, fileRefsMap, description);
		} else {
			catFile = CatalogUtil.addNearestTemplate(name, fileRefsMap, description);
		}
		info(String.format("Template '%s' added to '%s'", name, catFile));
		return EXIT_OK;
	}

	private static AbstractMap.SimpleEntry<String, String> splitFileRef(String fileRef) {
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
			target = null;
		}
		return new AbstractMap.SimpleEntry<>(target, source);
	}

	private static boolean refExists(AbstractMap.SimpleEntry<String, String> splitRef) {
		String source = splitRef.getValue();
		ResourceRef resourceRef = ResourceRef.forResource(source);
		if (resourceRef == null || !resourceRef.getFile().canRead()) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"File could not be found or read: '" + source + "'");
		}
		return true;
	}

	private static Map.Entry<String, String> ensureTarget(Map.Entry<String, String> splitRef) {
		if (splitRef.getKey() == null) {
			String source = splitRef.getValue();
			Path t = Paths.get(source).normalize();
			String target = t.getFileName().toString();
			if (target.endsWith(".qute")) {
				target = target.substring(0, target.length() - 5);
			}
			return new AbstractMap.SimpleEntry<>(target, source);
		} else {
			return splitRef;
		}
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined templates or from the given catalog.")
class TemplateList extends BaseTemplateCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the template")
	boolean showOrigin;

	@CommandLine.Option(names = { "--show-files" }, description = "Show list of files for each template")
	boolean showFiles;

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
			printTemplatesWithOrigin(out, catalogName, catalog, showFiles);
		} else {
			printTemplates(out, catalogName, catalog, showFiles);
		}
		return EXIT_OK;
	}

	static void printTemplates(PrintStream out, String catalogName, Catalog catalog, boolean showFiles) {
		catalog.templates
							.keySet()
							.stream()
							.sorted()
							.forEach(name -> printTemplate(out, catalogName, catalog, name, showFiles, 0));
	}

	static void printTemplatesWithOrigin(PrintStream out, String catalogName, Catalog catalog, boolean showFiles) {
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
					.forEach(k -> printTemplate(out, catalogName, catalog, k, showFiles, 3));
		});
	}

	private static void printTemplate(PrintStream out, String catalogName, Catalog catalog,
			String name, boolean showFiles, int indent) {
		dev.jbang.catalog.Template template = catalog.templates.get(name);
		String fullName = catalogName != null ? name + "@" + catalogName : name;
		out.print(Util.repeat(" ", indent));
		if (template.description != null) {
			out.println(yellow(fullName) + " = " + template.description);
		} else {
			out.println(yellow(fullName) + " = ");
		}
		if (showFiles) {
			for (String dest : template.fileRefs.keySet()) {
				String ref = template.fileRefs.get(dest);
				if (ref == null || ref.isEmpty()) {
					ref = dest;
				}
				ref = template.resolve(ref);
				out.print(Util.repeat(" ", indent));
				if (ref.equals(dest)) {
					out.println("   " + ref);
				} else {
					out.println("   " + dest + " (from " + ref + ")");
				}
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
			CatalogUtil.removeNearestTemplate(name);
		}
		return EXIT_OK;
	}
}
