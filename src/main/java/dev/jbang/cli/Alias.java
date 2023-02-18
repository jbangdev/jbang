package dev.jbang.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.ConsoleOutput;
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

	@CommandLine.Option(names = { "--description",
			"-d" }, description = "A description for the alias")
	String description;

	@CommandLine.Option(names = { "-R", "--runtime-option",
			"--jave-options" }, description = "Options to pass to the Java runtime")
	List<String> javaRuntimeOptions;

	@CommandLine.Option(names = { "--name" }, description = "A name for the alias")
	String name;

	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams;

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

		Path catFile = getCatalog(false);
		if (catFile != null) {
			CatalogUtil.addAlias(catFile, name, scriptMixin.scriptOrFile, desc, userParams, javaRuntimeOptions,
					scriptMixin.sources, scriptMixin.resources, dependencyInfoMixin.getDependencies(),
					dependencyInfoMixin.getRepositories(), dependencyInfoMixin.getClasspaths(),
					dependencyInfoMixin.getProperties(), buildMixin.javaVersion, buildMixin.main,
					buildMixin.module, buildMixin.compileOptions, nativeMixin.nativeImage, nativeMixin.nativeOptions);
		} else {
			catFile = CatalogUtil.addNearestAlias(name, scriptMixin.scriptOrFile, desc, userParams, javaRuntimeOptions,
					scriptMixin.sources, scriptMixin.resources, dependencyInfoMixin.getDependencies(),
					dependencyInfoMixin.getRepositories(), dependencyInfoMixin.getClasspaths(),
					dependencyInfoMixin.getProperties(), buildMixin.javaVersion, buildMixin.main,
					buildMixin.module, buildMixin.compileOptions, nativeMixin.nativeImage, nativeMixin.nativeOptions);
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
									.nativeOptions(nativeMixin.nativeOptions);
		Path cat = getCatalog(false);
		if (cat != null) {
			pb.catalog(cat.toFile());
		}
		return pb;
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
		return groups	.entrySet()
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
				out.println(ConsoleOutput.bold(cat.resourceRef));
				cat.aliases.forEach(a -> printAlias(out, a, 3));
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
	}

	private static AliasOut getAliasOut(String catalogName, Catalog catalog, String name) {
		dev.jbang.catalog.Alias alias = catalog.aliases.get(name);
		String catName = catalogName != null ? catalogName : Catalog.findImplicitName(alias.catalog);
		String fullName = catName != null ? name + "@" + Catalog.simplifyName(catName) : name;
		String scriptRef = alias.scriptRef;
		if (!catalog.aliases.containsKey(scriptRef)
				&& !Catalog.isValidCatalogReference(scriptRef)) {
			scriptRef = alias.resolve();
		}

		AliasOut out = new AliasOut();
		out.name = name;
		out.catalogName = catName != null ? Catalog.simplifyName(catName) : null;
		out.fullName = fullName;
		out.scriptRef = scriptRef;
		out.description = alias.description;
		out.arguments = alias.arguments;
		out.javaVersion = alias.javaVersion;
		out.mainClass = alias.mainClass;
		out.javaOptions = alias.runtimeOptions;
		out.properties = alias.properties;
		out._catalogRef = alias.catalog.catalogRef;
		return out;
	}

	private static void printAlias(PrintStream out, AliasOut alias, int indent) {
		out.print(Util.repeat(" ", indent));
		String prefix = Util.repeat(" ", alias.fullName.length() + indent);
		if (alias.description != null) {
			out.println(ConsoleOutput.yellow(alias.fullName) + " = " + alias.description);
			if (Util.isVerbose())
				out.println(prefix + ConsoleOutput.faint("   (" + alias.scriptRef + ")"));
		} else {
			out.println(ConsoleOutput.yellow(alias.fullName) + " = " + alias.scriptRef);
		}
		if (alias.arguments != null) {
			out.println(prefix + ConsoleOutput.cyan("   Arguments: ") + String.join(" ", alias.arguments));
		}
		if (alias.javaVersion != null) {
			out.println(prefix + ConsoleOutput.cyan("   Java Version: ") + alias.javaVersion);
		}
		if (alias.mainClass != null) {
			out.println(prefix + ConsoleOutput.cyan("   Main Class: ") + alias.mainClass);
		}
		if (alias.javaOptions != null) {
			out.println(prefix + ConsoleOutput.cyan("   Java Options: ") + String.join(" ", alias.javaOptions));
		}
		if (alias.properties != null) {
			out.println(prefix + ConsoleOutput.magenta("   Properties: ") + alias.properties);
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
