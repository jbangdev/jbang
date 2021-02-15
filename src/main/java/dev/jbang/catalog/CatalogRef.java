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

public class CatalogRef {
	@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
	public final String catalogRef;
	public final String description;

	CatalogRef(String catalogRef, String description) {
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
		if (Catalog.isAbsoluteRef(catalogRef) || Files.isRegularFile(Paths.get(catalogRef))) {
			Catalog cat = Catalog.getByRef(catalogRef);
			return new CatalogRef(catalogRef, cat.description);
		} else {
			Optional<String> url = ImplicitCatalogRef.getImplicitCatalogUrl(catalogRef);
			if (!url.isPresent()) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Unable to locate catalog: " + catalogRef);
			}
			Catalog cat = Catalog.getByRef(url.get());
			return new CatalogRef(url.get(), cat.description);
		}
	}

	static CatalogRef get(Path cwd, String catalogName) {
		CatalogRef catalogRef = null;
		Catalog catalog = findNearestCatalogWithCatalogRef(cwd, catalogName);
		if (catalog != null) {
			catalogRef = catalog.catalogs.get(catalogName);
		}
		if (catalogRef == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			Optional<String> url = ImplicitCatalogRef.getImplicitCatalogUrl(catalogName);
			if (url.isPresent()) {
				Catalog implicitCatalog = Catalog.getByRef(url.get());
				catalogRef = CatalogUtil.addCatalogRef(cwd, Settings.getUserImplicitCatalogFile(), catalogName,
						url.get(),
						implicitCatalog.description);
			}
		}
		return catalogRef;
	}

	static Catalog findNearestCatalogWithCatalogRef(Path dir, String catalogName) {
		return Catalog.findNearestCatalogWith(dir, catalogFile -> {
			Catalog catalog = Catalog.get(catalogFile);
			return catalog.catalogs.containsKey(catalogName);
		});
	}
}
