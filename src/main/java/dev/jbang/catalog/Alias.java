package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import javax.annotation.Nonnull;

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
	public final List<JavaAgent> javaAgents;

	public static class JavaAgent {
		@SerializedName(value = "agent-ref")
		@Nonnull
		public final String agentRef;
		@Nonnull
		public final String options;

		public JavaAgent(@Nonnull String agentRef, @Nonnull String options) {
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
				null, null, null, null, null, null, null, null, null);
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
			List<String> args = join(a1.arguments, a2.arguments);
			List<String> jopts = join(a1.runtimeOptions, a2.runtimeOptions);
			List<String> srcs = join(a1.sources, a2.sources);
			List<String> ress = join(a1.resources, a2.resources);
			List<String> deps = join(a1.dependencies, a2.dependencies);
			List<String> repos = join(a1.repositories, a2.repositories);
			List<String> cpaths = join(a1.classpaths, a2.classpaths);
			Map<String, String> props = join(a1.properties, a2.properties);
			String javaVersion = a1.javaVersion != null ? a1.javaVersion : a2.javaVersion;
			String mainClass = a1.mainClass != null ? a1.mainClass : a2.mainClass;
			String moduleName = a1.moduleName != null ? a1.moduleName : a2.moduleName;
			List<String> copts = join(a1.compileOptions, a2.compileOptions);
			List<String> nopts = join(a1.nativeOptions, a2.nativeOptions);
			Boolean nimg = a1.nativeImage != null ? a1.nativeImage : a2.nativeImage;
			Boolean ints = a1.integrations != null ? a1.integrations : a2.integrations;
			String jfr = a1.jfr != null ? a1.jfr : a2.jfr;
			Map<String, String> debug = join(a1.debug, a2.debug);
			Boolean cds = a1.cds != null ? a1.cds : a2.cds;
			Boolean inter = a1.interactive != null ? a1.interactive : a2.interactive;
			Boolean ep = a1.enablePreview != null ? a1.enablePreview : a2.enablePreview;
			Boolean ea = a1.enableAssertions != null ? a1.enableAssertions : a2.enableAssertions;
			Boolean esa = a1.enableSystemAssertions != null ? a1.enableSystemAssertions : a2.enableSystemAssertions;
			Map<String, String> mopts = join(a1.manifestOptions, a2.manifestOptions);
			List<JavaAgent> jags = join(a1.javaAgents, a2.javaAgents);
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;
			return new Alias(a2.scriptRef, desc, args, jopts, srcs, ress, deps, repos, cpaths, props, javaVersion,
					mainClass, moduleName, copts, nimg, nopts, ints, jfr, debug, cds, inter, ep, ea, esa, mopts, jags,
					catalog);
		} else {
			return a1;
		}
	}

	private static <T> List<T> join(List<T> l1, List<T> l2) {
		if (l1 != null && !l1.isEmpty()) {
			if (l2 != null && !l2.isEmpty()) {
				List<T> merged = new ArrayList<>(l1.size() + l2.size());
				merged.addAll(l2);
				merged.addAll(l1);
				return merged;
			} else {
				return l1;
			}
		} else {
			return l2;
		}
	}

	private static <K, V> Map<K, V> join(Map<K, V> m1, Map<K, V> m2) {
		if (m1 != null && !m1.isEmpty()) {
			if (m2 != null && !m2.isEmpty()) {
				Map<K, V> merged = new HashMap<>();
				merged.putAll(m2);
				merged.putAll(m1);
				return merged;
			} else {
				return m1;
			}
		} else {
			return m2;
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
				nativeOptions, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, catalog);
	}

	public Alias withScriptRef(String scriptRef) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, catalog);
	}
}
