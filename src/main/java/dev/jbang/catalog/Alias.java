package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.annotations.SerializedName;

import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class Alias extends CatalogItem {
	@SerializedName(value = "script-ref", alternate = { "scriptRef" })
	public final String scriptRef;
	public final String description;
	public final List<String> arguments;
	@SerializedName(value = "java-options")
	public final List<String> javaOptions;
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

	private Alias() {
		this(null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	public Alias(String scriptRef,
			String description,
			List<String> arguments,
			List<String> javaOptions,
			List<String> sources,
			List<String> resources,
			List<String> dependencies,
			List<String> repositories,
			List<String> classpaths,
			Map<String, String> properties,
			String javaVersion,
			String mainClass,
			Catalog catalog) {
		super(catalog);
		this.scriptRef = scriptRef;
		this.description = description;
		this.arguments = arguments;
		this.javaOptions = javaOptions;
		this.sources = sources;
		this.resources = resources;
		this.dependencies = dependencies;
		this.repositories = repositories;
		this.classpaths = classpaths;
		this.properties = properties;
		this.javaVersion = javaVersion;
		this.mainClass = mainClass;
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
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
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
			List<String> opts = a1.javaOptions != null && !a1.javaOptions.isEmpty() ? a1.javaOptions
					: a2.javaOptions;
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
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;
			return new Alias(a2.scriptRef, desc, args, opts, srcs, ress, deps, repos, cpaths, props, javaVersion,
					mainClass,
					catalog);
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
		return Catalog.findNearestCatalogWith(dir, catalog -> catalog.aliases.containsKey(aliasName));
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
}
