package dev.jbang.catalog;

import static dev.jbang.catalog.Catalog.isValidCatalogReference;
import static dev.jbang.util.Util.entry;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.util.Util;

public class CatalogUtil {

	private static final String validNameChars = "-.\\w";
	private static final Pattern validNamePattern = Pattern.compile("[" + validNameChars + "]+");

	/**
	 * Determines if an alias with the given name exists in the given catalog
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        The name of alias
	 */
	public static boolean hasAlias(Path catalogFile, String name) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		return catalog.aliases.containsKey(name);
	}

	/**
	 * Adds a new alias to the nearest catalog
	 * 
	 * @param name The name of the new alias
	 */
	public static Path addNearestAlias(String name, Alias alias) {
		Path catalogFile = Catalog.getCatalogFile(null);
		addAlias(catalogFile, name, alias);
		return catalogFile;
	}

	/**
	 * Adds a new alias to the given catalog
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the new alias
	 * @param alias       The alias to add
	 */
	public static Alias addAlias(Path catalogFile, String name, Alias alias) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		String scriptRef = catalog.relativize(alias.scriptRef);
		catalog.aliases.put(name, alias.withCatalog(catalog).withScriptRef(scriptRef));
		try {
			catalog.write();
			return alias;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Finds the nearest catalog file that contains an alias with the given name and
	 * removes it
	 * 
	 * @param name Name of alias to remove
	 */
	public static void removeNearestAlias(String name) {
		Catalog catalog = Alias.findNearestCatalogWithAlias(Util.getCwd(), name);
		if (catalog != null) {
			if (catalog.catalogRef.isURL() && Util.isRemoteRef(catalog.catalogRef.getOriginalResource())) {
				Util.warnMsg("Unable to remove alias " + name + " because it is imported from a remote catalog");
			} else if (catalog.equals(Catalog.getBuiltin())) {
				Util.warnMsg("Cannot remove alias " + name + " from built-in catalog");
			} else {
				removeAlias(catalog, name);
			}
		}
	}

	/**
	 * Remove alias from specified catalog file
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        Name of alias to remove
	 */
	public static void removeAlias(Path catalogFile, String name) {
		Catalog catalog = Catalog.get(catalogFile);
		removeAlias(catalog, name);
	}

	private static void removeAlias(Catalog catalog, String name) {
		if (catalog.aliases.containsKey(name)) {
			catalog.aliases.remove(name);
			try {
				catalog.write();
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}

	/**
	 * Determines if a template with the given name exists in the given catalog
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the template
	 */
	public static boolean hasTemplate(Path catalogFile, String name) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		return catalog.templates.containsKey(name);
	}

	/**
	 * Adds a new template to the nearest catalog
	 *
	 * @param name       The name of the new template
	 * @param properties Properties that the template uses and can be used during
	 *                   script initialization to customize the script.
	 */
	public static Path addNearestTemplate(String name, Map<String, String> fileRefs, String description,
			Map<String, TemplateProperty> properties) {
		Path catalogFile = Catalog.getCatalogFile(null);
		addTemplate(catalogFile, name, fileRefs, description, properties);
		return catalogFile;
	}

	/**
	 * Adds a new template to the given catalog
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the new template
	 * @param properties  Properties that the template uses and can be used during
	 *                    script initialization to customize the script.
	 */
	public static Template addTemplate(Path catalogFile, String name, Map<String, String> fileRefs,
			String description, Map<String, TemplateProperty> properties) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		Map<String, String> relFileRefs = fileRefs.entrySet()
			.stream()
			.map(e -> entry(e.getKey(), catalog.relativize(e.getValue())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Template template = new Template(relFileRefs, description, properties, catalog);
		catalog.templates.put(name, template);
		try {
			catalog.write();
			return template;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add template: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Finds the nearest catalog file that contains an template with the given name
	 * and removes it
	 *
	 * @param name Name of template to remove
	 */
	public static void removeNearestTemplate(String name) {
		Catalog catalog = Template.findNearestCatalogWithTemplate(Util.getCwd(), name);
		if (catalog != null) {
			if (catalog.catalogRef.isURL() && Util.isRemoteRef(catalog.catalogRef.getOriginalResource())) {
				Util.warnMsg("Unable to remove template " + name + " because it is imported from a remote catalog");
			} else if (catalog.equals(Catalog.getBuiltin())) {
				Util.warnMsg("Cannot remove template " + name + " from built-in catalog");
			} else {
				removeTemplate(catalog, name);
			}
		}
	}

	/**
	 * Remove template from specified catalog file
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        Name of template to remove
	 */
	public static void removeTemplate(Path catalogFile, String name) {
		Catalog catalog = Catalog.get(catalogFile);
		removeTemplate(catalog, name);
	}

	private static void removeTemplate(Catalog catalog, String name) {
		if (catalog.templates.containsKey(name)) {
			catalog.templates.remove(name);
			try {
				catalog.write();
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove template: " + ex.getMessage());
			}
		}
	}

	/**
	 * Finds the nearest catalog file that contains a catalog ref with the given
	 * name and removes it
	 *
	 * @param name Name of catalog ref to remove
	 */
	public static void removeNearestCatalogRef(String name) {
		Catalog catalog = CatalogRef.findNearestCatalogWithCatalogRef(Util.getCwd(), name);
		if (catalog != null) {
			if (catalog.catalogRef.isURL() && Util.isRemoteRef(catalog.catalogRef.getOriginalResource())) {
				Util.warnMsg("Unable to remove catalog ref " + name + " because it is imported from a remote catalog");
			} else if (catalog.equals(Catalog.getBuiltin())) {
				Util.warnMsg("Cannot remove catalog ref " + name + " from built-in catalog");
			} else {
				removeCatalogRef(catalog, name);
			}
		}
	}

	public static void removeCatalogRef(Path catalogFile, String name) {
		Catalog catalog = Catalog.get(catalogFile);
		removeCatalogRef(catalog, name);
	}

	public static void removeCatalogRef(Catalog catalog, String name) {
		if (catalog.catalogs.containsKey(name)) {
			catalog.catalogs.remove(name);
			try {
				catalog.write();
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

	/**
	 * Determines if a catalog ref with the given name exists in the given catalog
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the catalog ref
	 */
	public static boolean hasCatalogRef(Path catalogFile, String name) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		return catalog.catalogs.containsKey(name);
	}

	/**
	 * Adds a new catalog ref to the nearest catalog file
	 *
	 * @param name The name of the new alias
	 */
	public static Path addNearestCatalogRef(String name, String catalogRef, String description, Boolean importItems) {
		Path catalogFile = Catalog.getCatalogFile(null);
		addCatalogRef(catalogFile, name, catalogRef, description, importItems);
		return catalogFile;
	}

	public static CatalogRef addCatalogRef(Path catalogFile, String name, String catalogRef,
			String description, Boolean importItems) {
		Path cwd = Util.getCwd();
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		try {
			Path cat = Paths.get(catalogRef);
			if (!cat.isAbsolute() && Files.isRegularFile(cat)) {
				catalogRef = cat.toAbsolutePath().toString();
			}
			if (!Util.isAbsoluteRef(catalogRef)) {
				Optional<String> url = ImplicitCatalogRef.resolveImplicitCatalogUrl(catalogRef);
				if (url.isPresent()) {
					catalogRef = url.get();
				}
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
		CatalogRef ref = new CatalogRef(catalogRef, description, importItems, catalog);
		catalog.catalogs.put(name, ref);
		try {
			catalog.write();
			return ref;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add catalog: " + ex.getMessage());
			return null;
		}
	}

	public static String nameFromRef(String ref) {
		String startName = null;
		String name;
		if (isValidCatalogReference(ref)) {
			// If the script ref is an alias we take that name up to
			// the @-symbol (if any) to be the command name.
			startName = ref;
			name = startName;
			int p = name.indexOf("@");
			if (p > 0) {
				name = name.substring(0, p);
			}
		} else if (DependencyUtil.looksLikeAGav(ref)) {
			MavenCoordinate coord = MavenCoordinate.fromString(ref);
			name = coord.getArtifactId();
		} else {
			// If the script is a file or a URL we take the last part of
			// the name without extension (if any) to be the command name.
			try {
				URI u = new URI(ref);
				startName = u.getPath();
				if (startName.endsWith("/")) { // if using default app use the last segment.
					startName = startName.substring(0, startName.length() - 1);
				}
				startName = u.getPath().substring(Math.max(0, startName.lastIndexOf("/")));
			} catch (URISyntaxException e) {
				startName = Paths.get(ref).getFileName().toString();
			}

			// Remove the extension. If that extension happens to be ".qute"
			// we (try to) remove an additional extension.
			name = startName.endsWith(".qute") ? Util.base(Util.base(startName)) : Util.base(startName);

			name = name.replaceAll("[^" + validNameChars + "]", "");

			// If the name ends with a hyphen and a version-like segment (e.g. my-app-1.2.3,
			// my-app-1.2.a, my-app-1.2-rc1),
			// strip that off, but keep hyphens in the rest of the name. Accept versions
			// with digits, dots, and letters.
			// but my-app stays my-app.
			if (name.matches(".*-[0-9][0-9A-Za-z.-]*$")) {
				int lastDash = name.lastIndexOf("-");
				name = name.substring(0, lastDash);
			}

		}
		if (!isValidName(name)) {
			throw new IllegalArgumentException(
					"A valid command name could not be determined from: '" + startName + "'");
		}
		return name;
	}

	public static boolean isValidName(String name) {
		return validNamePattern.matcher(name).matches();
	}

	public static String catalogRef(String atName) {
		String[] parts = atName.split("@", 2);
		return parts.length == 2 ? parts[1] : null;
	}
}
