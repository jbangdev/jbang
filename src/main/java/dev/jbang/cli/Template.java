package dev.jbang.cli;

import static dev.jbang.util.Util.entry;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

@CommandDefinition(name = "template", description = "Manage templates for scripts.", groupCommands = {
		Template.TemplateAdd.class, Template.TemplateList.class,
		Template.TemplateRemove.class }, generateHelp = true, helpGroup = "Configuration")
public class Template extends BaseCommand {
	public static final Pattern TPL_FILENAME_PATTERN = Pattern.compile("\\{filename}", Pattern.CASE_INSENSITIVE);
	public static final Pattern TPL_BASENAME_PATTERN = Pattern.compile("\\{basename}", Pattern.CASE_INSENSITIVE);

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	static abstract class BaseTemplateCommand extends BaseCommand {

		@Mixin
		CatalogFileOptionsMixin catalogOptions;
	}

	@CommandDefinition(name = "add", description = "Add template for script reference.", generateHelp = true)
	public static class TemplateAdd extends BaseTemplateCommand {

		@Option(shortName = 'd', name = "description", description = "A description for the template")
		String description;

		@Option(name = "name", description = "A name for the template")
		String name;

		@Option(name = "force", hasValue = false, description = "Force overwriting of existing template")
		boolean force;

		@Arguments(paramLabel = "files", arity = "1..*", description = "Paths or URLs to template files", required = true)
		List<String> fileRefs;

		@OptionList(shortName = 'P', name = "property", description = "Template property (key=description::defaultValue)")
		List<String> propertyStrings;

		@Override
		public Integer doCall() throws IOException {
			if (name != null && !Catalog.isValidName(name)) {
				throw new ExitException(EXIT_INVALID_INPUT,
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
					String refName = firstRef.getValue();
					String ext = refName.endsWith(".qute") ? Util.extension(Util.base(refName))
							: Util.extension(refName);
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

			Map<String, TemplateProperty> propertiesMap = parseProperties(propertyStrings);

			Path catFile = catalogOptions.getCatalogOrDefault();
			if (force || !CatalogUtil.hasTemplate(catFile, name)) {
				CatalogUtil.addTemplate(catFile, name, fileRefsMap, description, propertiesMap);
			} else {
				Util.infoMsg("A template with name '" + name + "' already exists, use '--force' to add anyway.");
				return EXIT_INVALID_INPUT;
			}
			info(String.format("Template '%s' added to '%s'", name, catFile));
			return EXIT_OK;
		}

		static Map<String, TemplateProperty> parseProperties(List<String> propStrings) {
			if (propStrings == null || propStrings.isEmpty()) {
				return new HashMap<>();
			}
			Map<String, TemplateProperty> result = new HashMap<>();
			for (String prop : propStrings) {
				String key;
				String desc = null;
				String defVal = null;
				int eqIdx = prop.indexOf('=');
				if (eqIdx >= 0) {
					key = prop.substring(0, eqIdx);
					String rest = prop.substring(eqIdx + 1);
					int sepIdx = rest.indexOf("::");
					if (sepIdx >= 0) {
						desc = rest.substring(0, sepIdx);
						defVal = rest.substring(sepIdx + 2);
					} else {
						desc = rest;
					}
				} else {
					String[] parts = prop.split(":", 3);
					key = parts[0];
					if (parts.length > 1) {
						desc = parts[1];
					}
					if (parts.length > 2) {
						defVal = parts[2];
					}
				}
				if (desc != null && desc.isEmpty()) {
					desc = null;
				}
				if (defVal != null && defVal.isEmpty()) {
					defVal = null;
				}
				if (key == null || key.isEmpty()) {
					throw new IllegalArgumentException(
							"Invalid property definition, key must not be empty: '" + prop + "'");
				}
				result.put(key, new TemplateProperty(desc, defVal));
			}
			return result;
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
	}

	@CommandDefinition(name = "list", description = "Lists locally defined templates or from the given catalog.", generateHelp = true)
	public static class TemplateList extends BaseTemplateCommand {

		@Option(name = "show-origin", hasValue = false, description = "Show the origin of the template")
		boolean showOrigin;

		@Option(name = "show-files", hasValue = false, description = "Show list of files for each template")
		boolean showFiles;

		@Option(name = "show-properties", hasValue = false, description = "Show list of properties for each template")
		boolean showProperties;

		@Argument(paramLabel = "name", description = "The name of a catalog")
		String catalogName;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		private static final int INDENT_SIZE = 3;

		@Override
		public Integer doCall() throws IOException {
			PrintStream out = System.out;
			Catalog catalog;
			Path cat = catalogOptions.getCatalog(true);
			if (catalogName != null) {
				catalog = Catalog.getByName(catalogName);
			} else if (cat != null) {
				catalog = Catalog.get(cat);
			} else {
				catalog = Catalog.getMerged(true, false);
			}
			if (showOrigin) {
				printTemplatesWithOrigin(out, catalogName, catalog, showFiles, showProperties, format);
			} else {
				printTemplates(out, catalogName, catalog, showFiles, showProperties, format);
			}
			return EXIT_OK;
		}

		static void printTemplates(PrintStream out, String catalogName, Catalog catalog, boolean showFiles,
				boolean showProperties, OutputFormat format) {
			List<TemplateOut> templates = catalog.templates
				.keySet()
				.stream()
				.sorted()
				.map(name -> getTemplateOut(catalogName, catalog, name,
						showFiles, showProperties))
				.collect(Collectors.toList());
			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(templates, out);
			} else {
				templates.forEach(t -> printTemplate(out, t, 0));
			}
		}

		static List<dev.jbang.cli.Catalog.CatalogList.CatalogOut> getTemplatesWithOrigin(String catalogName,
				dev.jbang.catalog.Catalog catalog, boolean showFiles,
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
				.map(e -> new dev.jbang.cli.Catalog.CatalogList.CatalogOut(null, e.getKey(),
						null, e.getValue(), null))
				.collect(Collectors.toList());
		}

		static void printTemplatesWithOrigin(PrintStream out, String catalogName, dev.jbang.catalog.Catalog catalog,
				boolean showFiles,
				boolean showProperties, OutputFormat format) {
			List<dev.jbang.cli.Catalog.CatalogList.CatalogOut> catalogs = getTemplatesWithOrigin(catalogName, catalog,
					showFiles, showProperties);
			if (format == OutputFormat.json) {
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

		private static TemplateOut getTemplateOut(String catalogName, dev.jbang.catalog.Catalog catalog, String name,
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
			out.println(prefix1 + dev.jbang.cli.Catalog.CatalogList.getColoredFullName(template.fullName));
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

	@CommandDefinition(name = "remove", description = "Remove existing template.", generateHelp = true)
	public static class TemplateRemove extends BaseTemplateCommand {

		@Argument(paramLabel = "name", description = "The name of the template", required = true)
		String name;

		@Override
		public Integer doCall() throws IOException {
			final Path cat = catalogOptions.getCatalog(true);
			if (cat != null) {
				CatalogUtil.removeTemplate(cat, name);
			} else {
				CatalogUtil.removeNearestTemplate(name);
			}
			return EXIT_OK;
		}
	}
}
