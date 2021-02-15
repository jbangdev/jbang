package dev.jbang.catalog;

import static dev.jbang.catalog.Catalog.isValidCatalogReference;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.Settings;
import dev.jbang.util.Util;

public class CatalogUtil {
	public static final String JBANG_DOT_DIR = ".jbang";

	private static final String validNameChars = "-.\\w";
	private static final Pattern validNamePattern = Pattern.compile("[" + validNameChars + "]+");

	/**
	 * Adds a new alias to the nearest catalog
	 * 
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new alias
	 */
	public static Path addNearestAlias(Path cwd, String name, String scriptRef, String description,
			List<String> arguments,
			Map<String, String> properties) {
		Path catalogFile = Catalog.getCatalogFile(cwd, null);
		addAlias(cwd, catalogFile, name, scriptRef, description, arguments, properties);
		return catalogFile;
	}

	/**
	 * Adds a new alias to the given catalog
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the new alias
	 */
	public static Alias addAlias(Path cwd, Path catalogFile, String name, String scriptRef, String description,
			List<String> arguments,
			Map<String, String> properties) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		scriptRef = catalog.relativize(cwd, scriptRef);
		Alias alias = new Alias(scriptRef, description, arguments, properties, catalog);
		catalog.aliases.put(name, alias);
		try {
			Catalog.write(catalogFile, catalog);
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
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of alias to remove
	 */
	public static void removeNearestAlias(Path cwd, String name) {
		Catalog catalog = Alias.findNearestCatalogWithAlias(cwd, name);
		if (catalog != null) {
			removeAlias(catalog, name);
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
				Catalog.write(catalog.catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}

	/**
	 * Adds a new template to the nearest catalog
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new template
	 */
	public static Path addNearestTemplate(Path cwd, String name, List<String> fileRefs, String description) {
		Path catalogFile = Catalog.getCatalogFile(cwd, null);
		addTemplate(cwd, catalogFile, name, fileRefs, description);
		return catalogFile;
	}

	/**
	 * Adds a new template to the given catalog
	 *
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the new template
	 */
	public static Template addTemplate(Path cwd, Path catalogFile, String name, List<String> fileRefs,
			String description) {
		final Path cwdf = cwd == null ? Util.getCwd() : cwd;
		catalogFile = cwdf.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		List<String> relFileRefs = fileRefs	.stream()
											.map(ref -> catalog.relativize(cwdf, ref))
											.collect(Collectors.toList());
		Template template = new Template(relFileRefs, description, catalog);
		catalog.templates.put(name, template);
		try {
			Catalog.write(catalogFile, catalog);
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
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of template to remove
	 */
	public static void removeNearestTemplate(Path cwd, String name) {
		Catalog catalog = Template.findNearestCatalogWithTemplate(cwd, name);
		if (catalog != null) {
			removeTemplate(catalog, name);
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
				Catalog.write(catalog.catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove template: " + ex.getMessage());
			}
		}
	}

	/**
	 * Finds the nearest catalog file that contains a catalog ref with the given
	 * name and removes it
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of catalog ref to remove
	 */
	public static void removeNearestCatalogRef(Path cwd, String name) {
		Catalog catalog = CatalogRef.findNearestCatalogWithCatalogRef(cwd, name);
		if (catalog != null) {
			removeCatalogRef(catalog, name);
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
				Catalog.write(catalog.catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

	/**
	 * Adds a new catalog ref to the nearest catalog file
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new alias
	 */
	public static Path addNearestCatalogRef(Path cwd, String name, String catalogRef, String description) {
		Path catalogFile = Catalog.getCatalogFile(cwd, null);
		addCatalogRef(cwd, catalogFile, name, catalogRef, description);
		return catalogFile;
	}

	public static CatalogRef addCatalogRef(Path cwd, Path catalogFile, String name, String catalogRef,
			String description) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = Catalog.get(catalogFile);
		try {
			Path cat = Paths.get(catalogRef);
			if (!cat.isAbsolute() && Files.isRegularFile(cat)) {
				catalogRef = cat.toAbsolutePath().toString();
			}
			if (!Catalog.isAbsoluteRef(catalogRef)) {
				Optional<String> url = ImplicitCatalogRef.getImplicitCatalogUrl(catalogRef);
				if (url.isPresent()) {
					catalogRef = url.get();
				}
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
		CatalogRef ref = new CatalogRef(catalogRef, description);
		catalog.catalogs.put(name, ref);
		try {
			Catalog.write(catalogFile, catalog);
			return ref;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add catalog: " + ex.getMessage());
			return null;
		}
	}

	static Path findNearestFileWith(Path dir, String fileName, Function<Path, Boolean> accept) {
		Path result = findNearestLocalFileWith(dir, fileName, accept);
		if (result == null) {
			Path file = Settings.getConfigDir().resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				result = file;
			}
		}
		return result;
	}

	private static Path findNearestLocalFileWith(Path dir, String fileName, Function<Path, Boolean> accept) {
		if (dir == null) {
			dir = Util.getCwd();
		}
		while (dir != null) {
			Path file = dir.resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				return file;
			}
			file = dir.resolve(JBANG_DOT_DIR).resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				return file;
			}
			dir = dir.getParent();
		}
		return null;
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

			name = startName;
			int p = name.lastIndexOf(".");
			if (p > 0) {
				name = name.substring(0, p);
			}
			name = name.replaceAll("[^" + validNameChars + "]", "");

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
}
