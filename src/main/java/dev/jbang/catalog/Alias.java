package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import dev.jbang.cli.ExitException;

public class Alias extends CatalogItem {
	@SerializedName(value = "script-ref", alternate = { "scriptRef" })
	public final String scriptRef;
	public final String description;
	public final List<String> arguments;
	public final Map<String, String> properties;

	public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties,
			Catalog catalog) {
		super(catalog);
		this.scriptRef = scriptRef;
		this.description = description;
		this.arguments = arguments;
		this.properties = properties;
	}

	/**
	 * This method returns the scriptRef of the Alias with all contextual modifiers
	 * like baseRefs and current working directories applied.
	 */
	public String resolve(Path cwd) {
		return resolve(cwd, scriptRef);
	}

	/**
	 * Returns an Alias object for the given name
	 *
	 * @param cwd       The current working directory (leave null to auto detect)
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(Path cwd, String aliasName) {
		return get(cwd, aliasName, null, null);
	}

	/**
	 * Returns an Alias object for the given name with the given arguments and
	 * properties applied to it. Or null if no alias with that name could be found.
	 *
	 * @param aliasName  The name of an Alias
	 * @param arguments  Optional arguments to apply to the Alias
	 * @param properties Optional properties to apply to the Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(Path cwd, String aliasName, List<String> arguments, Map<String, String> properties) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias(null, null, arguments, properties, null);
		Alias result = merge(cwd, alias, aliasName, names);
		return result.scriptRef != null ? result : null;
	}

	private static Alias merge(Path cwd, Alias a1, String name, HashSet<String> names) {
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}
		Alias a2;
		if (parts.length == 1) {
			a2 = getLocal(cwd, name);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + name + "'");
			}
			a2 = getCatalogAlias(parts[1], parts[0]);
		}
		if (a2 != null) {
			names.add(name);
			a2 = merge(cwd, a2, a2.scriptRef, names);
			List<String> args = a1.arguments != null && !a1.arguments.isEmpty() ? a1.arguments : a2.arguments;
			Map<String, String> props = a1.properties != null && !a1.properties.isEmpty() ? a1.properties
					: a2.properties;
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;
			return new Alias(a2.scriptRef, a2.description, args, props, catalog);
		} else {
			return a1;
		}
	}

	/**
	 * Returns the given Alias from the local file system
	 *
	 * @param cwd       The current working directory (leave null to auto detect)
	 * @param aliasName The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getLocal(Path cwd, String aliasName) {
		Catalog catalog = findNearestCatalogWithAlias(cwd, aliasName);
		if (catalog != null) {
			return catalog.aliases.getOrDefault(aliasName, null);
		}
		return null;
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 *
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getCatalogAlias(String catalogName, String aliasName) {
		Catalog catalog = Catalog.getByName(null, catalogName);
		Alias alias = catalog.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No alias found with name '" + aliasName + "'");
		}
		return alias;
	}

	static Catalog findNearestCatalogWithAlias(Path dir, String aliasName) {
		return Catalog.findNearestCatalogWith(dir, catalogFile -> {
			Catalog catalog = Catalog.get(catalogFile);
			return catalog.aliases.containsKey(aliasName);
		});
	}
}
