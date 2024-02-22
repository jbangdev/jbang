package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class CatalogRef extends CatalogItem {
	@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
	public final String catalogRef;
	public final String description;

	CatalogRef(String catalogRef, String description, Catalog catalog) {
		super(catalog);
		this.catalogRef = catalogRef;
		this.description = description;
	}

	/**
	 * Creates a CatalogRef for the given catalog
	 *
	 * @param catalogRef File path, full URL or implicit Catalog reference to a
	 *                   Catalog.
	 * @return A CatalogRef object
	 */
	public static CatalogRef createByRefOrImplicit(String catalogRef) {
		if (Util.isAbsoluteRef(catalogRef) || Files.isRegularFile(Paths.get(catalogRef))) {
			Catalog cat = Catalog.getByRef(catalogRef);
			return new CatalogRef(catalogRef, cat.description, cat);
		} else {
			Optional<String> url = ImplicitCatalogRef.getImplicitCatalogUrl(catalogRef);
			if (!url.isPresent()) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Unable to locate catalog: " + catalogRef);
			}
			Catalog cat = Catalog.getByRef(url.get());
			return new CatalogRef(url.get(), cat.description, cat);
		}
	}

	public static CatalogRef get(String catalogRefName) {
		String[] parts = catalogRefName.split("@", 2);
		if (parts[0].isEmpty()) {
			throw new RuntimeException("Invalid catalog ref name '" + catalogRefName + "'");
		}
		CatalogRef catalogRef;
		if (parts.length == 1) {
			catalogRef = getLocal(catalogRefName);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid catalog ref name '" + catalogRefName + "'");
			}
			catalogRef = fromCatalog(parts[1], parts[0]);
		}
		return catalogRef;
	}

	private static CatalogRef getLocal(String catalogName) {
		CatalogRef catalogRef = null;
		Catalog catalog = findNearestCatalogWithCatalogRef(Util.getCwd(), catalogName);
		if (catalog != null) {
			catalogRef = catalog.catalogs.get(catalogName);
		}
		if (catalogRef == null && Util.isValidPath(catalogName)) {
			Path p = Util.getCwd().resolve(catalogName);
			if (!p.getFileName().toString().equals(Catalog.JBANG_CATALOG_JSON)) {
				p = p.resolve(Catalog.JBANG_CATALOG_JSON);
			}
			if (Files.isRegularFile(p)) {
				catalogRef = createByRefOrImplicit(p.toString());
			}
		}
		if (catalogRef == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			Optional<String> url = ImplicitCatalogRef.getImplicitCatalogUrl(catalogName);
			if (url.isPresent()) {
				Catalog implicitCatalog = Catalog.getByRef(url.get());
				catalogRef = CatalogUtil.addCatalogRef(Settings.getUserImplicitCatalogFile(), catalogName,
						url.get(),
						implicitCatalog.description);
			}
		}
		return catalogRef;
	}

	/**
	 * Returns the given CatalogRef from the given registered Catalog
	 *
	 * @param catalogName    The name of a registered Catalog
	 * @param catalogRefName The name of a CatalogRef
	 * @return A Template object
	 */
	private static CatalogRef fromCatalog(String catalogName, String catalogRefName) {
		Catalog catalog = Catalog.getByName(catalogName);
		CatalogRef catalogRef = catalog.catalogs.get(catalogRefName);
		if (catalogRef == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No catalog ref found with name '" + catalogRefName + "'");
		}
		return catalogRef;
	}

	static Catalog findNearestCatalogWithCatalogRef(Path dir, String catalogName) {
		return Catalog.findNearestCatalogWith(dir, catalog -> catalog.catalogs.containsKey(catalogName));
	}
}
