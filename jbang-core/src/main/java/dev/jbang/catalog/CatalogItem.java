package dev.jbang.catalog;

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
			ref = catalog.getScriptBase() + "/" + ref;
		}
		return ref;
	}

}
