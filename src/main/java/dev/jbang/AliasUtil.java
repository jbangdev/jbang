package dev.jbang;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import picocli.CommandLine;

public class AliasUtil {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";

	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";

	private static final String JBANG_CATALOG_REPO = "jbang-catalog";

	public static class Alias {
		@SerializedName(value = "script-ref", alternate = { "scriptRef" })
		public final String scriptRef;
		public final String description;
		public final List<String> arguments;
		public final Map<String, String> properties;

		public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties) {
			this.scriptRef = scriptRef;
			this.description = description;
			this.arguments = arguments;
			this.properties = properties;
		}
	}

	public static class Aliases {
		public Map<String, Alias> aliases = new HashMap<>();
		@SerializedName(value = "base-ref", alternate = { "baseRef" })
		public String baseRef;
		public String description;
	}

	public static class Catalog {
		@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
		public final String catalogRef;
		public final String description;

		Catalog(String catalogRef, String description) {
			this.catalogRef = catalogRef;
			this.description = description;
		}
	}

	public static class CatalogInfo {
		Map<String, Catalog> catalogs = new HashMap<>();
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
	public static Alias getAlias(String aliasName, List<String> arguments, Map<String, String> properties) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias(null, null, arguments, properties);
		Alias result = mergeAliases(alias, aliasName, names);
		return result.scriptRef != null ? result : null;
	}

	private static Alias mergeAliases(Alias a1, String name, HashSet<String> names) {
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}
		Alias a2;
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
			return new Alias(a2.scriptRef, null, args, props);
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
	public static Alias getCatalogAlias(String catalogName, String aliasName) {
		Aliases aliases = getCatalogAliasesByName(catalogName, false);
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
	public static Aliases getCatalogAliasesByName(String catalogName, boolean updateCache) {
		if (catalogName == null) {
			return Settings.getAliasesFromLocalCatalog();
		}
		Catalog catalog = getCatalog(catalogName);
		if (catalog != null) {
			return getCatalogAliasesByRef(catalog.catalogRef, updateCache);
		} else {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unknown catalog '" + catalogName + "'");
		}
	}

	private static Catalog getCatalog(String catalogName) {
		Catalog catalog = Settings.getCatalogs().get(catalogName);
		if (catalog == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			ImplicitCatalogRef icr = ImplicitCatalogRef.parse(catalogName);
			Optional<String> url;
			url = chain(
					() -> tryDownload(icr.url(GITHUB_URL, "/blob/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITHUB_URL, "/blob/")) : Optional.empty(),
					() -> tryDownload(icr.url(GITLAB_URL, "/-/blob/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITLAB_URL, "/-/blob/")) : Optional.empty(),
					() -> tryDownload(icr.url(BITBUCKET_URL, "/src/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(BITBUCKET_URL, "/src/")) : Optional.empty())
																													.findFirst();
			if (url.isPresent()) {
				Aliases aliases = AliasUtil.getCatalogAliasesByRef(url.get(), false);
				catalog = Settings.addCatalog(catalogName, url.get(), aliases.description);
			}
		}
		return catalog;
	}

	private static class ImplicitCatalogRef {
		final String org;
		final String repo;
		final String ref; // Branch or Commit
		final String path;

		private ImplicitCatalogRef(String org, String repo, String ref, String path) {
			this.org = org;
			this.repo = repo;
			this.ref = ref;
			this.path = path;
		}

		public boolean isPossibleCommit() {
			return ref.matches("[0-9a-f]{5,40}");
		}

		public String url(String host, String infix) {
			return host + org + "/" + repo + infix + ref + "/" + path + JBANG_CATALOG_JSON;
		}

		public static ImplicitCatalogRef parse(String name) {
			String[] parts = name.split("~", 2);
			String path;
			if (parts.length == 2) {
				path = parts[1] + "/";
			} else {
				path = "";
			}
			String[] names = parts[0].split("/");
			if (names.length > 3) {
				throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Invalid catalog name '" + name + "'");
			}
			String org = names[0];
			String repo;
			if (names.length >= 2 && !names[1].isEmpty()) {
				repo = names[1];
			} else {
				repo = JBANG_CATALOG_REPO;
			}
			String ref;
			if (names.length == 3 && !names[2].isEmpty()) {
				ref = names[2];
			} else {
				ref = "master";
			}
			return new ImplicitCatalogRef(org, repo, ref, path);
		}
	}

	private static Optional<String> tryDownload(String url) {
		try {
			getCatalogAliasesByRef(url, false);
			Util.verboseMsg("Catalog found at " + url);
			return Optional.of(url);
		} catch (Exception ex) {
			Util.verboseMsg("No catalog found at " + url, ex);
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
	public static Aliases getCatalogAliasesByRef(String catalogRef, boolean updateCache) {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		Path catalogPath = null;
		try {
			catalogPath = Util.obtainFile(catalogRef, updateCache);
			Aliases aliases = Settings.getAliasesFromCatalog(catalogPath, updateCache);
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
		} catch (IOException | JsonParseException ex) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE,
					"Unable to download catalog: " + catalogRef + " via " + catalogPath, ex);
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
	public static Alias getCatalogAlias(Aliases aliases, String aliasName) {
		Alias alias = aliases.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "No alias found with name '" + aliasName + "'");
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
			alias = new Alias(ref, alias.description, alias.arguments, alias.properties);
		}
		// TODO if we have to combine the baseUrl with the scriptRef
		// we need to make a copy of the Alias with the full URL
		return alias;
	}

	static Aliases readAliasesFromCatalog(Path catalogPath) {
		Aliases aliases;
		aliases = new Aliases();
		if (Files.isRegularFile(catalogPath)) {
			try (Reader in = Files.newBufferedReader(catalogPath)) {
				Gson parser = new Gson();
				aliases = parser.fromJson(in, Aliases.class);
				// Validate the result (Gson can't do this)
				check(aliases.aliases != null, "Missing required attribute 'aliases'");
				for (String aliasName : aliases.aliases.keySet()) {
					Alias alias = aliases.aliases.get(aliasName);
					check(alias.scriptRef != null, "Missing required attribute 'aliases.script-ref'");
				}
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return aliases;
	}

	static void writeAliasesToCatalog(Path catalogPath) throws IOException {
		try (Writer out = Files.newBufferedWriter(catalogPath)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(Settings.getAliasesFromLocalCatalog(), out);
		}
	}

	static void check(boolean ok, String message) {
		if (!ok) {
			throw new JsonParseException(message);
		}
	}

	static CatalogInfo readCatalogInfo(Path catalogsPath) {
		CatalogInfo info;
		if (Files.isRegularFile(catalogsPath)) {
			try (Reader in = Files.newBufferedReader(catalogsPath)) {
				Gson parser = new Gson();
				info = parser.fromJson(in, CatalogInfo.class);
				// Validate the result (Gson can't do this)
				check(info.catalogs != null, "Missing required attribute 'catalogs'");
				for (String catName : info.catalogs.keySet()) {
					Catalog cat = info.catalogs.get(catName);
					check(cat.catalogRef != null, "Missing required attribute 'catalogs.catalogRef'");
				}
			} catch (IOException e) {
				info = new CatalogInfo();
			}
		} else {
			info = new CatalogInfo();
		}
		return info;
	}

	static void writeCatalogInfo(Path catalogPath) throws IOException {
		try (Writer out = Files.newBufferedWriter(catalogPath)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(Settings.getCatalogInfo(), out);
		}
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
