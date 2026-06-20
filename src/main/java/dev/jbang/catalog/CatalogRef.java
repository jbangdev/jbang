package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class CatalogRef extends CatalogItem {
	@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
	public final String catalogRef;
	public final String description;
	@SerializedName(value = "import")
	public final Boolean importItems;

	CatalogRef(String catalogRef, String description, Boolean importItems, Catalog catalog) {
		super(catalog);
		this.catalogRef = catalogRef;
		this.description = description;
		this.importItems = importItems;
	}

	public static CatalogRef get(String catalogRefString) {
		String[] parts = catalogRefString.split("@", 2);
		if (parts[0].isEmpty()) {
			throw new RuntimeException("Invalid catalog ref name '" + catalogRefString + "'");
		}
		CatalogRef catalogRef = null;
		if (parts.length == 1) {
			if (!Util.isRemoteRef(catalogRefString)) {
				catalogRef = getLocal(catalogRefString);
			}
			if (catalogRef == null) {
				catalogRef = getRemote(catalogRefString);
			}
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid catalog ref name '" + catalogRefString + "'");
			}
			catalogRef = fromCatalog(parts[1], parts[0]);
		}
		return catalogRef;
	}

	private static CatalogRef getLocal(String catalogRefString) {
		CatalogRef catalogRef = null;
		Catalog catalog = findNearestCatalogWithCatalogRef(Util.getCwd(), catalogRefString);
		if (catalog != null) {
			catalogRef = catalog.catalogs.get(catalogRefString);
		}
		if (catalogRef == null && Util.isValidPath(catalogRefString)) {
			Path p = Util.getCwd().resolve(catalogRefString);
			if (!p.getFileName().toString().equals(Catalog.JBANG_CATALOG_JSON)) {
				p = p.resolve(Catalog.JBANG_CATALOG_JSON);
			}
			if (Files.isRegularFile(p)) {
				Catalog cat = Catalog.getByRef(p.toString());
				catalogRef = new CatalogRef(p.toString(), cat.description, null, cat);
			}
		}
		return catalogRef;
	}

	private static CatalogRef getRemote(String catalogRefString) {
		CatalogRef catalogRef = null;
		Optional<String> url;
		if (Util.isRemoteRef(catalogRefString) && Util.isURL(catalogRefString)) {
			url = Optional.of(catalogRefString);
		} else {
			Util.verboseMsg("Local catalog '" + catalogRefString + "' not found, trying implicit catalogs...");
			url = ImplicitCatalogRef.resolveImplicitCatalogUrl(catalogRefString);
		}
		if (url.isPresent()) {
			if (url.get().equals(catalogRefString)) {
				catalogRef = new CatalogRef(url.get(), null, null, null);
			} else {
				// Store the resolved URL for future reference
				Catalog implicitCatalog = Catalog.getByRef(url.get());
				catalogRef = CatalogUtil.addCatalogRef(Settings.getUserImplicitCatalogFile(), catalogRefString,
						url.get(), implicitCatalog.description, null);
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
		CatalogRef cr = get(catalogName);
		Catalog catalog = Catalog.getByRef(cr.catalogRef);
		CatalogRef catalogRef = catalog.catalogs.get(catalogRefName);
		if (catalogRef == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No catalog ref found with name '" + catalogRefName + "'");
		}
		return catalogRef;
	}

	static Catalog findNearestCatalogWithCatalogRef(Path dir, String catalogName) {
		return Catalog.findNearestCatalogWith(dir, true, true,
				catalog -> catalog.catalogs.containsKey(catalogName) ? catalog : null);
	}
}
