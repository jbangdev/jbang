package dev.jbang.catalog;

import java.nio.file.Path;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import dev.jbang.util.Util;

public class Template extends CatalogItem {
	@SerializedName(value = "file-refs")
	public final Map<String, String> fileRefs;
	public final String description;

	public Template(Map<String, String> fileRefs, String description, Catalog catalog) {
		super(catalog);
		this.fileRefs = fileRefs;
		this.description = description;
	}

	public static Template get(String templateName) {
		Template template = null;
		Catalog catalog = findNearestCatalogWithTemplate(Util.getCwd(), templateName);
		if (catalog != null) {
			template = catalog.templates.get(templateName);
		}
		return template;
	}

	static Catalog findNearestCatalogWithTemplate(Path dir, String templateName) {
		return Catalog.findNearestCatalogWith(dir, catalogFile -> {
			Catalog catalog = Catalog.get(catalogFile);
			return catalog.templates.containsKey(templateName);
		});
	}
}
