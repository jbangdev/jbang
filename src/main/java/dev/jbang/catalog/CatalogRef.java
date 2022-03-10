package dev.jbang.catalog;

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

	static CatalogRef get(String catalogName) {
		CatalogRef catalogRef = null;
		Catalog catalog = findNearestCatalogWithCatalogRef(Util.getCwd(), catalogName);
		if (catalog != null) {
			catalogRef = catalog.catalogs.get(catalogName);
		}
		if (catalogRef == null && Util.isValidPath(catalogName)) {
			Path p = Paths.get(catalogName);
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

	static Catalog findNearestCatalogWithCatalogRef(Path dir, String catalogName) {
		return Catalog.findNearestCatalogWith(dir, catalog -> catalog.catalogs.containsKey(catalogName));
	}
}
