package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class Template extends CatalogItem {
	@SerializedName(value = "file-refs")
	public final Map<String, String> fileRefs;
	public final String description;
	public final Map<String, TemplateProperty> properties;

	public Template(Map<String, String> fileRefs, String description, Map<String, TemplateProperty> properties,
			Catalog catalog) {
		super(catalog);
		this.fileRefs = fileRefs;
		this.description = description;
		this.properties = properties;
	}

	public static Template get(String templateName) {
		String[] parts = templateName.split("@", 2);
		if (parts[0].isEmpty()) {
			throw new RuntimeException("Invalid template name '" + templateName + "'");
		}
		Template template;
		if (parts.length == 1) {
			template = getLocal(templateName);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid template name '" + templateName + "'");
			}
			template = fromCatalog(parts[1], parts[0]);
		}
		return template;
	}

	/**
	 * Returns the given Template from the local file system
	 *
	 * @param templateName The name of a Template
	 * @return A Template object
	 */
	private static Template getLocal(String templateName) {
		Catalog catalog = findNearestCatalogWithTemplate(Util.getCwd(), templateName);
		if (catalog != null) {
			return catalog.templates.getOrDefault(templateName, null);
		}
		return null;
	}

	static Catalog findNearestCatalogWithTemplate(Path dir, String templateName) {
		return Catalog.findNearestCatalogWith(dir, true, true,
				catalog -> catalog.templates.containsKey(templateName) ? catalog : null);
	}

	/**
	 * Returns the given Template from the given registered Catalog
	 *
	 * @param catalogName  The name of a registered Catalog
	 * @param templateName The name of a Template
	 * @return A Template object
	 */
	private static Template fromCatalog(String catalogName, String templateName) {
		Catalog catalog = Catalog.getByName(catalogName);
		Template template = catalog.templates.get(templateName);
		if (template == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No template found with name '" + templateName + "'");
		}
		return template;
	}
}
