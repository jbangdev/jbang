package dev.jbang.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.command.option.Option;

import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;

public class CatalogFileOptionsMixin {

	@Option(shortName = 'g', name = "global", hasValue = false, description = "Use the global (user) catalog file")
	boolean global;

	@Option(shortName = 'f', name = "file", description = "Path to the catalog file to use")
	File catalogFile;

	public Path getCatalogOrDefault() {
		Path cat = getCatalog(false);
		return cat != null ? cat : Catalog.getCatalogFile(null);
	}

	public Path getCatalog(boolean strict) {
		Path cat;
		if (global) {
			cat = Settings.getUserCatalogFile();
		} else {
			Path catPath = catalogFile != null ? catalogFile.toPath() : null;
			if (catPath != null && Files.isDirectory(catPath)) {
				Path defaultCatalog = catPath.resolve(Catalog.JBANG_CATALOG_JSON);
				Path hiddenCatalog = catPath.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
				if (!Files.exists(defaultCatalog) && Files.exists(hiddenCatalog)) {
					cat = hiddenCatalog;
				} else {
					cat = defaultCatalog;
				}
			} else {
				cat = catPath;
			}
			if (strict && cat != null && !Files.isRegularFile(cat)) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Catalog file not found at: " + catalogFile);
			}
		}
		return cat;
	}
}
