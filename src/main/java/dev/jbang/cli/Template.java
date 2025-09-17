package dev.jbang.cli;

import static dev.jbang.util.Util.entry;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "template", description = "Manage templates for scripts.", subcommands = {
		TemplateAdd.class, TemplateList.class, TemplateRemove.class })
public class Template {
	public static final Pattern TPL_FILENAME_PATTERN = Pattern.compile("\\{filename}", Pattern.CASE_INSENSITIVE);
	public static final Pattern TPL_BASENAME_PATTERN = Pattern.compile("\\{basename}", Pattern.CASE_INSENSITIVE);

	@CommandLine.Mixin
	HelpMixin helpMixin;
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
				Path hiddenCatalog = catalogFile.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
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

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwriting of existing template")
	boolean force;

	@CommandLine.Parameters(paramLabel = "files", index = "0..*", arity = "1..*", description = "Paths or URLs to template files")
	List<String> fileRefs;

	@CommandLine.Option(names = { "--property",
			"-P" }, description = "Template property", converter = TemplatePropertyConverter.class)
	List<TemplatePropertyInput> properties;

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
				String name = firstRef.getValue();
				String ext = name.endsWith(".qute") ? Util.extension(Util.base(name)) : Util.extension(name);
				String target = ext.isEmpty() ? "{filename}" : "{basename}." + ext;
				splitRefs.set(0, entry(target, firstRef.getValue()));
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

		Map<String, TemplateProperty> propertiesMap = Optional.ofNullable(properties)
			.map(Collection::stream)
			.map(stream -> stream.collect(Collectors.toMap(
					TemplatePropertyInput::getKey,
					(TemplatePropertyInput templatePropertyInput) -> new TemplateProperty(
							templatePropertyInput.getDescription(),
							templatePropertyInput.getDefaultValue()))))
			.orElse(new HashMap<>());

		Path catFile = getCatalog(false);
		if (catFile == null) {
			catFile = Catalog.getCatalogFile(null);
		}
		if (force || !CatalogUtil.hasTemplate(catFile, name)) {
			CatalogUtil.addTemplate(catFile, name, fileRefsMap, description, propertiesMap);
		} else {
			Util.infoMsg("A template with name '" + name + "' already exists, use '--force' to add anyway.");
			return EXIT_INVALID_INPUT;
		}
		info(String.format("Template '%s' added to '%s'", name, catFile));
		return EXIT_OK;
	}

	private static Map.Entry<String, String> splitFileRef(String fileRef) {
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
		return entry(target, source);
	}

	private static boolean refExists(Map.Entry<String, String> splitRef) {
		String source = splitRef.getValue();
		ResourceRef resourceRef = ResourceRef.forResource(source);
		if (resourceRef == null || !Files.isReadable(resourceRef.getFile())) {
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
			return entry(target, source);
		} else {
			return splitRef;
		}
	}

	public static class TemplatePropertyInput {
		private String key;
		private String description;
		private String defaultValue;

		public TemplatePropertyInput() {
		}

		public TemplatePropertyInput(String key, String description, String defaultValue) {
			this.key = key;
			this.description = description;
			this.defaultValue = defaultValue;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(String defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			TemplatePropertyInput that = (TemplatePropertyInput) o;

			if (key != null ? !key.equals(that.key) : that.key != null)
				return false;
			if (description != null ? !description.equals(that.description) : that.description != null)
				return false;
			return defaultValue != null ? defaultValue.equals(that.defaultValue) : that.defaultValue == null;
		}

		@Override
		public int hashCode() {
			int result = key != null ? key.hashCode() : 0;
			result = 31 * result + (description != null ? description.hashCode() : 0);
			result = 31 * result + (defaultValue != null ? defaultValue.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "TemplatePropertyInput{" +
					"key='" + key + '\'' +
					", description='" + description + '\'' +
					", defaultValue='" + defaultValue + '\'' +
					'}';
		}
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined templates or from the given catalog.")
class TemplateList extends BaseTemplateCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the template")
	boolean showOrigin;

	@CommandLine.Option(names = { "--show-files" }, description = "Show list of files for each template")
	boolean showFiles;

	@CommandLine.Option(names = { "--show-properties" }, description = "Show list of properties for each template")
	boolean showProperties;

	@CommandLine.Parameters(paramLabel = "catalogName", index = "0", description = "The name of a catalog", arity = "0..1")
	String catalogName;

	@CommandLine.Mixin
	FormatMixin formatMixin;

	private static final int INDENT_SIZE = 3;

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
			catalog = Catalog.getMerged(true, false);
		}
		if (showOrigin) {
			printTemplatesWithOrigin(out, catalogName, catalog, showFiles, showProperties, formatMixin.format);
		} else {
			printTemplates(out, catalogName, catalog, showFiles, showProperties, formatMixin.format);
		}
		return EXIT_OK;
	}

	static void printTemplates(PrintStream out, String catalogName, Catalog catalog, boolean showFiles,
			boolean showProperties, FormatMixin.Format format) {
		List<TemplateOut> templates = catalog.templates
			.keySet()
			.stream()
			.sorted()
			.map(name -> getTemplateOut(catalogName, catalog, name,
					showFiles, showProperties))
			.collect(Collectors.toList());
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(templates, out);
		} else {
			templates.forEach(t -> printTemplate(out, t, 0));
		}
	}

	static List<CatalogList.CatalogOut> getTemplatesWithOrigin(String catalogName, Catalog catalog, boolean showFiles,
			boolean showProperties) {
		Map<ResourceRef, List<TemplateOut>> groups = catalog.templates
			.keySet()
			.stream()
			.sorted()
			.map(name -> getTemplateOut(catalogName,
					catalog, name, showFiles,
					showProperties))
			.collect(Collectors.groupingBy(
					t -> t._catalogRef));
		return groups.entrySet()
			.stream()
			.map(e -> new CatalogList.CatalogOut(null, e.getKey(),
					null, e.getValue(), null))
			.collect(Collectors.toList());
	}

	static void printTemplatesWithOrigin(PrintStream out, String catalogName, Catalog catalog, boolean showFiles,
			boolean showProperties, FormatMixin.Format format) {
		List<CatalogList.CatalogOut> catalogs = getTemplatesWithOrigin(catalogName, catalog, showFiles, showProperties);
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(catalogs, out);
		} else {
			catalogs.forEach(cat -> {
				out.println(ConsoleOutput.bold(dev.jbang.catalog.Catalog.simplifyRef(cat.resourceRef)));
				cat.templates.forEach(t -> printTemplate(out, t, 1));
			});
		}
	}

	static class TemplateOut {
		public String name;
		public String catalogName;
		public String fullName;
		public String description;
		public List<FileRefOut> fileRefs;
		public Map<String, TemplateProperty> properties;
		public transient ResourceRef _catalogRef;
	}

	static class FileRefOut {
		String source;
		String resolved;
		String destination;
	}

	private static TemplateOut getTemplateOut(String catalogName, Catalog catalog, String name,
			boolean showFiles, boolean showProperties) {
		dev.jbang.catalog.Template template = catalog.templates.get(name);
		String catName = catalogName != null ? dev.jbang.catalog.Catalog.simplifyRef(catalogName)
				: CatalogUtil.catalogRef(name);
		String fullName = catalogName != null ? name + "@" + catName : name;

		TemplateOut out = new TemplateOut();
		out.name = name;
		out.catalogName = catName;
		out.fullName = fullName;
		out.description = template.description;
		if (showFiles && template.fileRefs != null) {
			out.fileRefs = new ArrayList<>();
			for (String dest : template.fileRefs.keySet()) {
				String ref = template.fileRefs.get(dest);
				if (ref == null || ref.isEmpty()) {
					ref = dest;
				}
				FileRefOut fro = new FileRefOut();
				fro.source = ref;
				fro.destination = dest;
				fro.resolved = template.resolve(ref);
				out.fileRefs.add(fro);
			}
		}
		out.properties = showProperties ? template.properties : null;
		out._catalogRef = template.catalog.catalogRef;
		return out;
	}

	private static void printTemplate(PrintStream out, TemplateOut template, int indent) {
		String prefix1 = Util.repeat(" ", indent * INDENT_SIZE);
		String prefix2 = Util.repeat(" ", (indent + 1) * INDENT_SIZE);
		String prefix3 = Util.repeat(" ", (indent + 2) * INDENT_SIZE);
		out.print(Util.repeat(" ", indent));
		out.println(prefix1 + dev.jbang.cli.CatalogList.getColoredFullName(template.fullName));
		if (template.description != null) {
			out.println(prefix2 + template.description);
		}
		if (template.fileRefs != null) {
			out.println(prefix2 + "Files:");
			for (FileRefOut fro : template.fileRefs) {
				if (fro.resolved.equals(fro.destination)) {
					out.println(prefix3 + fro.resolved);
				} else {
					out.println(prefix3 + fro.destination + " (from " + fro.resolved + ")");
				}
			}
		}
		if (template.properties != null) {
			out.println(prefix2 + "Properties:");
			for (Map.Entry<String, TemplateProperty> entry : template.properties.entrySet()) {
				StringBuilder propertyLineBuilder = new StringBuilder()
					.append(prefix3)
					.append(ConsoleOutput.cyan(entry.getKey()))
					.append(" = ");
				if (entry.getValue().getDescription() != null) {
					propertyLineBuilder.append(entry.getValue().getDescription()).append(" ");
				}
				if (entry.getValue().getDefaultValue() != null) {
					propertyLineBuilder.append("[").append(entry.getValue().getDefaultValue()).append("]");
				}
				out.println(propertyLineBuilder);
			}
		}
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
