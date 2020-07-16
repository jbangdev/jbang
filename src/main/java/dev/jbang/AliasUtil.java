package dev.jbang;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;

import picocli.CommandLine;

public class AliasUtil {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";

	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";

	private static final String JBANG_CATALOG_REPO = "/jbang-catalog";

	/**
	 * Returns an Alias object for the given name with the given arguments and
	 * properties applied to it. Or null if no alias with that name could be found.
	 * 
	 * @param aliasName  The name of an Alias
	 * @param arguments  Optional arguments to apply to the Alias
	 * @param properties Optional properties to apply to the Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Settings.Alias getAlias(String aliasName, List<String> arguments, Map<String, String> properties) {
		HashSet<String> names = new HashSet<>();
		Settings.Alias alias = new Settings.Alias(null, null, arguments, properties);
		Settings.Alias result = mergeAliases(alias, aliasName, names);
		return result.scriptRef != null ? result : null;
	}

	private static Settings.Alias mergeAliases(Settings.Alias a1, String name, HashSet<String> names) {
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}
		Settings.Alias a2;
		if (parts.length == 1) {
			a2 = Settings.getAliases().get(name);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + name + "'");
			}
			a2 = getCatalogAlias(parts[1], parts[0]);
		}
		if (a2 != null) {
			names.add(name);
			a2 = mergeAliases(a2, a2.scriptRef, names);
			List<String> args = a1.arguments != null ? a1.arguments : a2.arguments;
			Map<String, String> props = a1.properties != null ? a1.properties : a2.properties;
			return new Settings.Alias(a2.scriptRef, null, args, props);
		} else {
			return a1;
		}
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 * 
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	public static Settings.Alias getCatalogAlias(String catalogName, String aliasName) {
		Settings.Aliases aliases = getCatalogAliasesByName(catalogName, false);
		return getCatalogAlias(aliases, aliasName);
	}

	/**
	 * Load a Catalog's aliases given the name of a previously registered Catalog
	 * 
	 * @param catalogName The name of a registered Catalog. Set to null to retrieve
	 *                    the Alias from the local aliases
	 * @param updateCache Set to true to ignore cached values
	 * @return An Aliases object
	 */
	public static Settings.Aliases getCatalogAliasesByName(String catalogName, boolean updateCache) {
		if (catalogName == null) {
			return Settings.getAliasesFromLocalCatalog();
		}
		Settings.Catalog catalog = getCatalog(catalogName);
		if (catalog != null) {
			return getCatalogAliasesByRef(catalog.catalogRef, updateCache);
		} else {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unknown catalog '" + catalogName + "'");
		}
	}

	private static Settings.Catalog getCatalog(String catalogName) {
		Settings.Catalog catalog = Settings.getCatalogs().get(catalogName);
		if (catalog == null) {
			Optional<String> url;
			if (catalogName.contains("/")) {
				url = chain(
						() -> tryDownload(GITHUB_URL + catalogName + "/blob/master/" + JBANG_CATALOG_JSON),
						() -> tryDownload(GITLAB_URL + catalogName + "/-/blob/master/" + JBANG_CATALOG_JSON),
						() -> tryDownload(BITBUCKET_URL + catalogName + "/src/master/" + JBANG_CATALOG_JSON))
																												.findFirst();
			} else {
				url = chain(
						() -> tryDownload(
								GITHUB_URL + catalogName + JBANG_CATALOG_REPO + "/blob/master/" + JBANG_CATALOG_JSON),
						() -> tryDownload(
								GITLAB_URL + catalogName + JBANG_CATALOG_REPO + "/-/blob/master" + JBANG_CATALOG_JSON),
						() -> tryDownload(BITBUCKET_URL + catalogName + JBANG_CATALOG_REPO + "/src/master"
								+ JBANG_CATALOG_JSON))
														.findFirst();
			}
			if (url.isPresent()) {
				Settings.Aliases aliases = AliasUtil.getCatalogAliasesByRef(url.get(), false);
				catalog = Settings.addCatalog(catalogName, url.get(), aliases.description);
			}
		}
		return catalog;
	}

	private static Optional<String> tryDownload(String url) {
		try {
			getCatalogAliasesByRef(url, false);
			return Optional.of(url);
		} catch (Exception ex) {
			return Optional.empty();
		}
	}

	/**
	 * Load a Catalog's aliases given a file path or URL
	 * 
	 * @param catalogRef  File path or URL to a Catalog JSON file. If this does not
	 *                    end in .json then jbang-catalog.json will be appended to
	 *                    the end.
	 * @param updateCache Set to true to ignore cached values
	 * @return An Aliases object
	 */
	public static Settings.Aliases getCatalogAliasesByRef(String catalogRef, boolean updateCache) {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		try {
			Path catalogPath = Util.obtainFile(catalogRef, updateCache);
			Settings.Aliases aliases = Settings.getAliasesFromCatalog(catalogPath, updateCache);
			int p = catalogRef.lastIndexOf('/');
			if (p > 0) {
				String catalogBaseRef = catalogRef.substring(0, p);
				if (aliases.baseRef != null) {
					if (!aliases.baseRef.startsWith("/") && !aliases.baseRef.contains(":")) {
						aliases.baseRef = catalogBaseRef + "/" + aliases.baseRef;
					}
				} else {
					aliases.baseRef = catalogBaseRef;
				}
			}
			return aliases;
		} catch (IOException ex) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unable to download catalog", ex);
		} catch (JsonParseException ex) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Error parsing catalog", ex);
		}
	}

	/**
	 * Returns the given Alias from the given Aliases object. The Alias returned
	 * from this function is not simply the Alias stored in the Aliases object but
	 * one that has the Aliases' baseRef (if defined) applied to it.
	 * 
	 * @param aliases   An Aliases object
	 * @param aliasName The name of an Alias
	 * @return An Alias object
	 */
	public static Settings.Alias getCatalogAlias(Settings.Aliases aliases, String aliasName) {
		Settings.Alias alias = aliases.aliases.get(aliasName);
		if (alias == null) {
			throw new RuntimeException("No alias found with name '" + aliasName + "'");
		}
		if (aliases.baseRef != null && !isAbsoluteRef(alias.scriptRef)) {
			String ref = aliases.baseRef;
			if (!ref.endsWith("/")) {
				ref += "/";
			}
			if (alias.scriptRef.startsWith("./")) {
				ref += alias.scriptRef.substring(2);
			} else {
				ref += alias.scriptRef;
			}
			alias = new Settings.Alias(ref, alias.description, alias.arguments, alias.properties);
		}
		// TODO if we have to combine the baseUrl with the scriptRef
		// we need to make a copy of the Alias with the full URL
		return alias;
	}

	private static boolean isAbsoluteRef(String ref) {
		return ref.startsWith("/") || ref.contains(":");
	}

	@SafeVarargs
	public static <T> Stream<T> chain(Supplier<Optional<T>>... suppliers) {
		return Arrays	.asList(suppliers)
						.stream()
						.map(Supplier::get)
						.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty));
	}
}
