package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.catalog.Alias.JavaAgent;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "alias", description = "Manage aliases for scripts.", subcommands = { AliasAdd.class,
		AliasList.class, AliasRemove.class })
public class Alias {
	@CommandLine.Mixin
	HelpMixin helpMixin;
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

@CommandLine.Command(name = "add", description = "Add alias for script reference.")
class AliasAdd extends BaseAliasCommand {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	BuildMixin buildMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Mixin
	NativeMixin nativeMixin;

	@CommandLine.Mixin
	RunMixin runMixin;

	@CommandLine.Option(names = { "--description" }, description = "A description for the alias")
	String description;

	@CommandLine.Option(names = { "--name" }, description = "A name for the alias")
	String name;

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwriting of existing alias")
	boolean force;

	@CommandLine.Option(names = { "--enable-preview" }, description = "Activate Java preview features")
	Boolean enablePreviewRequested;

	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams;

	@CommandLine.Option(names = { "--docs" }, description = "Documentation reference for the alias")
	List<String> docs;

	@Override
	public Integer doCall() {
		scriptMixin.validate();
		if (name != null && !Catalog.isValidName(name)) {
			throw new IllegalArgumentException(
					"Invalid alias name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
		}

		ProjectBuilder pb = createProjectBuilder();
		Project prj = pb.build(scriptMixin.scriptOrFile);
		if (name == null) {
			name = CatalogUtil.nameFromRef(scriptMixin.scriptOrFile);
		}

		String desc = description != null ? description : prj.getDescription().orElse(null);

		dev.jbang.catalog.Alias alias = new dev.jbang.catalog.Alias(scriptMixin.scriptOrFile, desc, userParams,
				runMixin.javaRuntimeOptions, scriptMixin.sources, scriptMixin.resources,
				dependencyInfoMixin.getDependencies(),
				dependencyInfoMixin.getRepositories(), dependencyInfoMixin.getClasspaths(),
				dependencyInfoMixin.getProperties(), buildMixin.javaVersion, buildMixin.main, buildMixin.module,
				buildMixin.compileOptions, nativeMixin.nativeImage, nativeMixin.nativeOptions,
				scriptMixin.forceType != null ? scriptMixin.forceType.name() : null, buildMixin.integrations,
				runMixin.flightRecorderString, runMixin.debugString, runMixin.cds, runMixin.interactive,
				enablePreviewRequested, runMixin.enableAssertions, runMixin.enableSystemAssertions,
				buildMixin.manifestOptions, createJavaAgents(), docs, null);
		Path catFile = getCatalog(false);
		if (catFile == null) {
			catFile = Catalog.getCatalogFile(null);
		}
		if (force || !CatalogUtil.hasAlias(catFile, name)) {
			CatalogUtil.addAlias(catFile, name, alias);
		} else {
			Util.infoMsg("A script with name '" + name + "' already exists, use '--force' to add anyway.");
			return EXIT_INVALID_INPUT;
		}
		info(String.format("Alias '%s' added to '%s'", name, catFile));
		return EXIT_OK;
	}

	ProjectBuilder createProjectBuilder() {
		ProjectBuilder pb = Project
			.builder()
			.setProperties(dependencyInfoMixin.getProperties())
			.additionalDependencies(dependencyInfoMixin.getDependencies())
			.additionalRepositories(dependencyInfoMixin.getRepositories())
			.additionalClasspaths(dependencyInfoMixin.getClasspaths())
			.additionalSources(scriptMixin.sources)
			.additionalResources(scriptMixin.resources)
			.forceType(scriptMixin.forceType)
			.javaVersion(buildMixin.javaVersion)
			.moduleName(buildMixin.module)
			.compileOptions(buildMixin.compileOptions)
			.nativeImage(nativeMixin.nativeImage)
			.nativeOptions(nativeMixin.nativeOptions)
			.integrations(buildMixin.integrations)
			.enablePreview(enablePreviewRequested)
			.jdkManager(buildMixin.jdkProvidersMixin.getJdkManager());
		Path cat = getCatalog(false);
		if (cat != null) {
			pb.catalog(cat.toFile());
		}
		return pb;
	}

	private List<JavaAgent> createJavaAgents() {
		if (runMixin.javaAgentSlots == null) {
			return Collections.emptyList();
		}
		return runMixin.javaAgentSlots.entrySet()
			.stream()
			.map(e -> new JavaAgent(e.getKey(), e.getValue()))
			.collect(Collectors.toList());
	}
}

@CommandLine.Command(name = "list", description = "Lists locally defined aliases or from the given catalog.")
class AliasList extends BaseAliasCommand {

	@CommandLine.Option(names = { "--show-origin" }, description = "Show the origin of the alias")
	boolean showOrigin;

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
			printAliasesWithOrigin(out, catalogName, catalog, formatMixin.format);
		} else {
			printAliases(out, catalogName, catalog, formatMixin.format);
		}
		return EXIT_OK;
	}

	static void printAliases(PrintStream out, String catalogName, Catalog catalog, FormatMixin.Format format) {
		List<AliasOut> aliases = catalog.aliases
			.keySet()
			.stream()
			.sorted()
			.map(name -> getAliasOut(catalogName, catalog, name))
			.collect(Collectors.toList());

		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(aliases, out);
		} else {
			aliases.forEach(a -> printAlias(out, a, 0));
		}
	}

	static List<CatalogList.CatalogOut> getAliasesWithOrigin(String catalogName, Catalog catalog) {
		Map<ResourceRef, List<AliasOut>> groups = catalog.aliases
			.keySet()
			.stream()
			.sorted()
			.map(name -> getAliasOut(catalogName, catalog,
					name))
			.collect(Collectors.groupingBy(
					a -> a._catalogRef));
		return groups.entrySet()
			.stream()
			.map(e -> new CatalogList.CatalogOut(null, e.getKey(),
					e.getValue(), null, null))
			.collect(Collectors.toList());
	}

	static void printAliasesWithOrigin(PrintStream out, String catalogName, Catalog catalog,
			FormatMixin.Format format) {
		List<CatalogList.CatalogOut> catalogs = getAliasesWithOrigin(catalogName, catalog);
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(catalogs, out);
		} else {
			catalogs.forEach(cat -> {
				out.println(ConsoleOutput.bold(dev.jbang.catalog.Catalog.simplifyRef(cat.resourceRef)));
				cat.aliases.forEach(a -> printAlias(out, a, 1));
			});
		}
	}

	static class AliasOut {
		public String name;
		public String catalogName;
		public String fullName;
		public String scriptRef;
		public String description;
		public List<String> arguments;
		public String javaVersion;
		public String mainClass;
		public List<String> javaOptions;
		public Map<String, String> properties;
		public transient ResourceRef _catalogRef;
		public Boolean enablePreview;
		public List<String> docsRef;
	}

	private static AliasOut getAliasOut(String catalogName, Catalog catalog, String name) {
		dev.jbang.catalog.Alias alias = catalog.aliases.get(name);
		String catName = catalogName != null ? Catalog.simplifyRef(catalogName) : CatalogUtil.catalogRef(name);
		String fullName = catalogName != null ? name + "@" + catName : name;
		String scriptRef = alias.scriptRef;
		if (!catalog.aliases.containsKey(scriptRef)
				&& !Catalog.isValidCatalogReference(scriptRef)) {
			scriptRef = alias.resolve();
		}

		AliasOut out = new AliasOut();
		out.name = name;
		out.catalogName = catName;
		out.fullName = fullName;
		out.scriptRef = scriptRef;
		out.description = alias.description;
		out.arguments = alias.arguments;
		out.javaVersion = alias.javaVersion;
		out.mainClass = alias.mainClass;
		out.javaOptions = alias.runtimeOptions;
		out.properties = alias.properties;
		out._catalogRef = alias.catalog.catalogRef;
		out.enablePreview = alias.enablePreview;
		out.docsRef = alias.docs;
		return out;
	}

	private static void printAlias(PrintStream out, AliasOut alias, int indent) {
		String prefix1 = Util.repeat(" ", indent * INDENT_SIZE);
		String prefix2 = Util.repeat(" ", (indent + 1) * INDENT_SIZE);
		String prefix3 = Util.repeat(" ", (indent + 2) * INDENT_SIZE);
		out.println(prefix1 + dev.jbang.cli.CatalogList.getColoredFullName(alias.fullName));
		if (alias.description != null) {
			out.println(prefix2 + alias.description);
		}
		if (alias.docsRef != null && !alias.docsRef.isEmpty()) {
			for (String ref : alias.docsRef) {
				out.println(prefix2 + ref);
			}
		}
		out.println(prefix2 + ConsoleOutput.faint(alias.scriptRef));
		if (alias.arguments != null) {
			out.println(prefix3 + ConsoleOutput.cyan("   Arguments: ") + String.join(" ", alias.arguments));
		}
		if (alias.javaVersion != null) {
			out.println(prefix3 + ConsoleOutput.cyan("   Java Version: ") + alias.javaVersion);
		}
		if (alias.mainClass != null) {
			out.println(prefix3 + ConsoleOutput.cyan("   Main Class: ") + alias.mainClass);
		}
		if (alias.enablePreview != null) {
			out.println(prefix3 + ConsoleOutput.cyan("   Enable Preview: ") + alias.enablePreview);
		}
		if (alias.javaOptions != null) {
			out.println(prefix3 + ConsoleOutput.cyan("   Java Options: ") + String.join(" ", alias.javaOptions));
		}
		if (alias.properties != null) {
			out.println(prefix3 + ConsoleOutput.magenta("   Properties: ") + alias.properties);
		}
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
