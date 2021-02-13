package dev.jbang.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import dev.jbang.Settings;
import dev.jbang.util.Util;

public class AliasUtil {
	public static final String JBANG_DOT_DIR = ".jbang";

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
		if (!Catalog.isRemoteRef(scriptRef) && !isValidCatalogReference(scriptRef)) {
			// If the scriptRef points to an existing file on the local filesystem
			// or it's obviously a path (but not an absolute path) we'll make it
			// relative to the location of the catalog we're adding the alias to.
			Path script = cwd.resolve(scriptRef).normalize();
			String baseRef = catalog.getScriptBase();
			if (!Catalog.isAbsoluteRef(scriptRef)
					&& !Catalog.isRemoteRef(baseRef)
					&& (!isValidName(scriptRef) || Files.isRegularFile(script))) {
				Path base = Paths.get(baseRef);
				if (base.getRoot().equals(script.getRoot())) {
					scriptRef = base.relativize(script.toAbsolutePath()).normalize().toString();
				} else {
					scriptRef = script.toAbsolutePath().normalize().toString();
				}
			}
			if (!Catalog.isRemoteRef(baseRef)
					&& !isValidName(scriptRef)
					&& !Files.isRegularFile(script)) {
				throw new IllegalArgumentException("Source file not found: " + scriptRef);
			}
		}
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
		Path catalog = Alias.findNearestCatalogWithAlias(cwd, name);
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
		if (catalog.aliases.containsKey(name)) {
			catalog.aliases.remove(name);
			try {
				Catalog.write(catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
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
		Path catalog = CatalogRef.findNearestCatalogWithCatalogRef(cwd, name);
		if (catalog != null) {
			removeCatalogRef(catalog, name);
		}
	}

	public static void removeCatalogRef(Path catalogFile, String name) {
		Catalog catalog = Catalog.get(catalogFile);
		if (catalog.catalogs.containsKey(name)) {
			catalog.catalogs.remove(name);
			try {
				Catalog.write(catalogFile, catalog);
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

	public static boolean isValidName(String name) {
		return name.matches("^[a-zA-Z][-\\w]*$");
	}

	public static boolean isValidCatalogReference(String name) {
		String[] parts = name.split("@");
		if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
			return false;
		}
		return isValidName(parts[0]);
	}

}
