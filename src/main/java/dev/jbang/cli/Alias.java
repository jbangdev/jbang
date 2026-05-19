package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.catalog.Alias.JavaAgent;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

@GroupCommandDefinition(name = "alias", description = "Manage aliases for scripts.", groupCommands = {
		Alias.AliasAdd.class, Alias.AliasList.class,
		Alias.AliasRemove.class }, generateHelp = true, helpGroup = "Configuration", defaultValueProvider = JBangDefaultValueProvider.class)
public class Alias extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	@CommandDefinition(name = "add", description = "Add alias for script reference.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class AliasAdd extends BaseCommand {

		@Mixin
		ScriptMixin scriptMixin;

		@Mixin
		BuildMixin buildMixin;

		@Mixin
		DependencyInfoMixin dependencyInfoMixin;

		@Mixin
		NativeMixin nativeMixin;

		@Mixin
		RunMixin runMixin;

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Mixin
		CatalogFileOptionsMixin catalogOptions;

		@Option(name = "description", description = "A description for the alias")
		String description;

		@Option(name = "name", description = "A name for the alias")
		String name;

		@Option(name = "force", hasValue = false, description = "Force overwriting of existing alias")
		boolean force;

		@Option(name = "enable-preview", hasValue = false, description = "Activate Java preview features")
		Boolean enablePreviewRequested;

		@Arguments(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
		List<String> userParams;

		@OptionList(name = "docs", description = "Documentation reference for the alias")
		List<String> docs;

		@Override
		public void afterParse() {
			super.afterParse();
			dependencyInfoMixin.applyIgnoreTransitiveRepositories();
			runMixin.resolveAfterParse();
		}

		@Override
		public Integer doCall() throws IOException {
			scriptMixin.validate();
			if (name != null && !Catalog.isValidName(name)) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"Invalid alias name, it should start with a letter followed by 0 or more letters, digits, underscores or hyphens");
			}

			ProjectBuilder pb = createAliasProjectBuilder();
			Project prj = pb.build(scriptMixin.scriptOrFile);
			if (name == null) {
				name = CatalogUtil.nameFromRef(scriptMixin.scriptOrFile);
			}

			String desc = description != null ? description : prj.getDescription().orElse(null);

			List<String> args = userParams != null && !userParams.isEmpty() ? userParams : null;
			dev.jbang.catalog.Alias alias = new dev.jbang.catalog.Alias(scriptMixin.scriptOrFile, desc, args,
					runMixin.javaRuntimeOptions, scriptMixin.sources, scriptMixin.resources,
					dependencyInfoMixin.getDependencies(),
					dependencyInfoMixin.getRepositories(), dependencyInfoMixin.getClasspaths(),
					dependencyInfoMixin.getProperties(), buildMixin.javaVersion, buildMixin.main, buildMixin.module,
					buildMixin.compileOptions, nativeMixin.nativeImage, nativeMixin.nativeOptions,
					scriptMixin.getForceType() != null ? scriptMixin.getForceType().name() : null,
					buildMixin.getIntegrations(),
					runMixin.flightRecorderString, runMixin.debugString, runMixin.getCds(), runMixin.interactive,
					enablePreviewRequested, runMixin.enableAssertions,
					runMixin.enableSystemAssertions,
					buildMixin.manifestOptions, createJavaAgents(), docs, null);
			Path catFile = catalogOptions.getCatalogOrDefault();
			if (force || !CatalogUtil.hasAlias(catFile, name)) {
				CatalogUtil.addAlias(catFile, name, alias);
			} else {
				Util.infoMsg("A script with name '" + name + "' already exists, use '--force' to add anyway.");
				return EXIT_INVALID_INPUT;
			}
			info(String.format("Alias '%s' added to '%s'", name, catFile));
			return EXIT_OK;
		}

		ProjectBuilder createAliasProjectBuilder() {
			ProjectBuilder pb = Project
				.builder()
				.setProperties(dependencyInfoMixin.getProperties())
				.additionalDependencies(dependencyInfoMixin.getDependencies())
				.additionalRepositories(dependencyInfoMixin.getRepositories())
				.additionalClasspaths(dependencyInfoMixin.getClasspaths())
				.additionalSources(scriptMixin.sources)
				.additionalResources(scriptMixin.resources)
				.forceType(scriptMixin.getForceType())
				.javaVersion(buildMixin.javaVersion)
				.moduleName(buildMixin.module)
				.compileOptions(buildMixin.compileOptions)
				.nativeImage(nativeMixin.nativeImage)
				.nativeOptions(nativeMixin.nativeOptions)
				.integrations(buildMixin.getIntegrations())
				.enablePreview(enablePreviewRequested)
				.jdkManager(jdkMixin.getJdkManager());
			Path cat = catalogOptions.getCatalog(false);
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

	@CommandDefinition(name = "list", description = "Lists locally defined aliases or from the given catalog.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class AliasList extends BaseCommand {

		@Mixin
		CatalogFileOptionsMixin catalogOptions;

		@Option(name = "show-origin", hasValue = false, description = "Show the origin of the alias")
		boolean showOrigin;

		@Argument(paramLabel = "catalogName", index = "0", arity = "0..1", description = "The name of a catalog")
		String catalogName;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		FormatMixin.Format format;

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
				printAliasesWithOrigin(out, catalogName, catalog, format);
			} else {
				printAliases(out, catalogName, catalog, format);
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

		static List<dev.jbang.cli.Catalog.CatalogList.CatalogOut> getAliasesWithOrigin(String catalogName,
				Catalog catalog) {
			Map<ResourceRef, List<AliasOut>> groups = catalog.aliases
				.keySet()
				.stream()
				.sorted()
				.map(name -> getAliasOut(catalogName, catalog,
						name))
				.collect(Collectors.groupingBy(
						a -> a._catalogRef,
						java.util.LinkedHashMap::new,
						Collectors.toList()));
			return groups.entrySet()
				.stream()
				.map(e -> new dev.jbang.cli.Catalog.CatalogList.CatalogOut(null, e.getKey(),
						e.getValue(), null, null))
				.collect(Collectors.toList());
		}

		static void printAliasesWithOrigin(PrintStream out, String catalogName, Catalog catalog,
				FormatMixin.Format format) {
			List<dev.jbang.cli.Catalog.CatalogList.CatalogOut> catalogs = getAliasesWithOrigin(catalogName, catalog);
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
			out.println(prefix1 + dev.jbang.cli.Catalog.CatalogList.getColoredFullName(alias.fullName));
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

	@CommandDefinition(name = "remove", description = "Remove existing alias.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class AliasRemove extends BaseCommand {

		@Mixin
		CatalogFileOptionsMixin catalogOptions;

		@Argument(paramLabel = "name", index = "0", arity = "1", description = "The name of the alias", required = true)
		String name;

		@Override
		public Integer doCall() throws IOException {
			final Path cat = catalogOptions.getCatalog(true);
			if (cat != null) {
				CatalogUtil.removeAlias(cat, name);
			} else {
				CatalogUtil.removeNearestAlias(name);
			}
			return EXIT_OK;
		}
	}
}
