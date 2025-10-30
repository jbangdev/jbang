package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.util.Util;

public class Alias extends CatalogItem {
	@SerializedName(value = "script-ref", alternate = { "scriptRef" })
	public final String scriptRef;
	public final String description;
	public final List<String> arguments;
	@SerializedName(value = "runtime-options", alternate = { "java-options" })
	public final List<String> runtimeOptions;
	public final List<String> sources;
	@SerializedName(value = "files")
	public final List<String> resources;
	public final List<String> dependencies;
	public final List<String> repositories;
	public final List<String> classpaths;
	public final Map<String, String> properties;
	@SerializedName(value = "java")
	public final String javaVersion;
	@SerializedName(value = "main")
	public final String mainClass;
	@SerializedName(value = "module")
	public final String moduleName;
	@SerializedName(value = "compile-options")
	public final List<String> compileOptions;
	@SerializedName(value = "native-image")
	public final Boolean nativeImage;
	@SerializedName(value = "native-options")
	public final List<String> nativeOptions;
	@SerializedName(value = "source-type")
	public final String forceType;
	public final Boolean integrations;
	public final String jfr;
	public final Map<String, String> debug;
	public final Boolean cds;
	public final Boolean interactive;
	@SerializedName(value = "enable-preview")
	public final Boolean enablePreview;
	@SerializedName(value = "enable-assertions")
	public final Boolean enableAssertions;
	@SerializedName(value = "enable-system-assertions")
	public final Boolean enableSystemAssertions;
	@SerializedName(value = "manifest-options")
	public final Map<String, String> manifestOptions;
	@SerializedName(value = "java-agents")
	@JsonAdapter(Catalog.SkipEmptyListSerializer.class)
	public final List<JavaAgent> javaAgents;
	@JsonAdapter(Catalog.SkipEmptyListSerializer.class)
	public final List<String> docs;

	public static class JavaAgent {
		@SerializedName(value = "agent-ref")
		@NonNull
		public final String agentRef;
		@NonNull
		public final String options;

		public JavaAgent() { // to make gson happy in native image
			this(null, null);
		}

		public JavaAgent(@NonNull String agentRef, @NonNull String options) {
			this.agentRef = agentRef;
			this.options = options;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			JavaAgent javaAgent = (JavaAgent) o;
			return agentRef.equals(javaAgent.agentRef) && options.equals(javaAgent.options);
		}

		@Override
		public int hashCode() {
			return Objects.hash(agentRef, options);
		}
	}

	public Alias() {
		this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
	}

	public Alias(String scriptRef,
			String description,
			List<String> arguments,
			List<String> runtimeOptions,
			List<String> sources,
			List<String> resources,
			List<String> dependencies,
			List<String> repositories,
			List<String> classpaths,
			Map<String, String> properties,
			String javaVersion,
			String mainClass,
			String moduleName,
			List<String> compileOptions,
			Boolean nativeImage,
			List<String> nativeOptions,
			String forceType,
			Boolean integrations,
			String jfr,
			Map<String, String> debug,
			Boolean cds,
			Boolean interactive,
			Boolean enablePreview,
			Boolean enableAssertions,
			Boolean enableSystemAssertions,
			Map<String, String> manifestOptions,
			List<JavaAgent> javaAgents,
			List<String> docs,
			Catalog catalog) {
		super(catalog);
		this.scriptRef = scriptRef;
		this.description = description;
		this.arguments = arguments;
		this.runtimeOptions = runtimeOptions;
		this.sources = sources;
		this.resources = resources;
		this.dependencies = dependencies;
		this.repositories = repositories;
		this.classpaths = classpaths;
		this.properties = properties;
		this.javaVersion = javaVersion;
		this.mainClass = mainClass;
		this.moduleName = moduleName;
		this.compileOptions = compileOptions;
		this.nativeImage = nativeImage;
		this.nativeOptions = nativeOptions;
		this.forceType = forceType;
		this.integrations = integrations;
		this.jfr = jfr;
		this.debug = debug;
		this.cds = cds;
		this.interactive = interactive;
		this.enablePreview = enablePreview;
		this.enableAssertions = enableAssertions;
		this.enableSystemAssertions = enableSystemAssertions;
		this.manifestOptions = manifestOptions;
		this.javaAgents = javaAgents;
		this.docs = docs;
	}

	/**
	 * This method returns the scriptRef of the Alias with all contextual modifiers
	 * like baseRefs and current working directories applied.
	 */
	public String resolve() {
		return resolve(scriptRef);
	}

	/**
	 * Returns an Alias object for the given name
	 *
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(String aliasName) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias();
		Alias result = merge(alias, aliasName, Alias::getLocal, names);
		return result.scriptRef != null ? result : null;
	}

	/**
	 * Returns an Alias object for the given name. The given Catalog will be used
	 * for any unqualified alias lookups.
	 *
	 * @param catalog   A Catalog to use for lookups
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(Catalog catalog, String aliasName) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias();
		Alias result = merge(alias, aliasName, catalog.aliases::get, names);
		return result.scriptRef != null ? result : null;
	}

	private static Alias merge(Alias a1, String name, Function<String, Alias> findUnqualifiedAlias,
			HashSet<String> names) {
		// if this is a proper possible GAV, i.e.
		// io.quarkiverse.mcp:artifact:1.0.0.Beta5@fatjar
		// don't try interpret it.
		if (DependencyUtil.looksLikeAPossibleGav(name)) {
			return a1;
		}
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@", 2);
		if (parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}
		Alias a2;
		if (parts.length == 1) {
			a2 = findUnqualifiedAlias.apply(name);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + name + "'");
			}
			a2 = fromCatalog(parts[1], parts[0]);
		}
		if (a2 != null) {
			names.add(name);
			a2 = merge(a2, a2.scriptRef, findUnqualifiedAlias, names);
			String desc = a1.description != null ? a1.description : a2.description;
			List<String> args = a1.arguments != null && !a1.arguments.isEmpty() ? a1.arguments : a2.arguments;
			List<String> jopts = a1.runtimeOptions != null && !a1.runtimeOptions.isEmpty() ? a1.runtimeOptions
					: a2.runtimeOptions;
			List<String> srcs = a1.sources != null && !a1.sources.isEmpty() ? a1.sources
					: a2.sources;
			List<String> ress = a1.resources != null && !a1.resources.isEmpty() ? a1.resources
					: a2.resources;
			List<String> deps = a1.dependencies != null && !a1.dependencies.isEmpty() ? a1.dependencies
					: a2.dependencies;
			List<String> repos = a1.repositories != null && !a1.repositories.isEmpty() ? a1.repositories
					: a2.repositories;
			List<String> cpaths = a1.classpaths != null && !a1.classpaths.isEmpty() ? a1.classpaths
					: a2.classpaths;
			Map<String, String> props = a1.properties != null && !a1.properties.isEmpty() ? a1.properties
					: a2.properties;
			String javaVersion = a1.javaVersion != null ? a1.javaVersion : a2.javaVersion;
			String mainClass = a1.mainClass != null ? a1.mainClass : a2.mainClass;
			String moduleName = a1.moduleName != null ? a1.moduleName : a2.moduleName;
			List<String> copts = a1.compileOptions != null && !a1.compileOptions.isEmpty() ? a1.compileOptions
					: a2.compileOptions;
			List<String> nopts = a1.nativeOptions != null && !a1.nativeOptions.isEmpty() ? a1.nativeOptions
					: a2.nativeOptions;
			String forceType = a1.forceType != null ? a1.forceType : a2.forceType;
			Boolean nimg = a1.nativeImage != null ? a1.nativeImage : a2.nativeImage;
			Boolean ints = a1.integrations != null ? a1.integrations : a2.integrations;
			String jfr = a1.jfr != null ? a1.jfr : a2.jfr;
			Map<String, String> debug = a1.debug != null ? a1.debug : a2.debug;
			Boolean cds = a1.cds != null ? a1.cds : a2.cds;
			Boolean inter = a1.interactive != null ? a1.interactive : a2.interactive;
			Boolean ep = a1.enablePreview != null ? a1.enablePreview : a2.enablePreview;
			Boolean ea = a1.enableAssertions != null ? a1.enableAssertions : a2.enableAssertions;
			Boolean esa = a1.enableSystemAssertions != null ? a1.enableSystemAssertions : a2.enableSystemAssertions;
			Map<String, String> mopts = a1.manifestOptions != null && !a1.manifestOptions.isEmpty() ? a1.manifestOptions
					: a2.manifestOptions;
			List<JavaAgent> jags = a1.javaAgents != null && !a1.javaAgents.isEmpty() ? a1.javaAgents : a2.javaAgents;
			List<String> docs = a1.docs != null && !a1.docs.isEmpty() ? a1.docs : a2.docs;
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;
			return new Alias(a2.scriptRef, desc, args, jopts, srcs, ress, deps, repos, cpaths, props, javaVersion,
					mainClass, moduleName, copts, nimg, nopts, forceType, ints, jfr, debug, cds, inter, ep, ea, esa,
					mopts, jags, docs, catalog);
		} else {
			return a1;
		}
	}

	/**
	 * Returns the given Alias from the local file system
	 *
	 * @param aliasName The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getLocal(String aliasName) {
		Catalog catalog = findNearestCatalogWithAlias(Util.getCwd(), aliasName);
		if (catalog != null) {
			return catalog.aliases.getOrDefault(aliasName, null);
		}
		return null;
	}

	static Catalog findNearestCatalogWithAlias(Path dir, String aliasName) {
		return Catalog.findNearestCatalogWith(dir, true, true,
				catalog -> catalog.aliases.containsKey(aliasName) ? catalog : null);
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 *
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias fromCatalog(String catalogName, String aliasName) {
		Catalog catalog = Catalog.getByName(catalogName);
		Alias alias = catalog.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No alias found with name '" + aliasName + "'");
		}
		return alias;
	}

	public Alias withCatalog(Catalog catalog) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}

	public Alias withScriptRef(String scriptRef) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}

	public Alias withForceType(String forceType) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}
}
