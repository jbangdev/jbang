package dev.jbang.catalog;

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
	public String resolve(String scriptRef) {
		String ref = scriptRef;
		if (!Util.isAbsoluteRef(ref)) {
			String base = catalog.getScriptBase();
			if (Util.isRemoteRef(base) || !Util.isValidPath(base)) {
				ref = base + "/" + ref;
			} else {
				ref = Paths.get(base).resolve(ref).toString();
			}
		}
		return ref;
	}

}
