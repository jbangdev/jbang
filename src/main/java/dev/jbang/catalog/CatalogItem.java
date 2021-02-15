package dev.jbang.catalog;

import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.util.Util;

abstract class CatalogItem {
	public transient Catalog catalog;

	public CatalogItem(Catalog catalog) {
		this.catalog = catalog;
	}

	/**
	 * This method returns the given scriptRef with all contextual modifiers like
	 * baseRefs and current working directories applied.
	 */
	public String resolve(Path cwd, String scriptRef) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		String ref = scriptRef;
		if (!Catalog.isAbsoluteRef(ref)) {
			ref = catalog.getScriptBase() + "/" + ref;
		}
		if (!Catalog.isRemoteRef(ref) && !Catalog.isClassPathRef(ref)) {
			Path script = Paths.get(ref).normalize();
			if (cwd.getRoot().equals(script.getRoot())) {
				script = cwd.relativize(script);
			} else {
				script = script.toAbsolutePath();
			}
			ref = script.toString();
		}
		return ref;
	}

}
