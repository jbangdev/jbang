package dev.jbang.catalog;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class Catalog {
	public Map<String, AliasUtil.CatalogRef> catalogs = new HashMap<>();
	public final Map<String, Alias> aliases = new HashMap<>();
	@SerializedName(value = "base-ref", alternate = { "baseRef" })
	public final String baseRef;
	public final String description;
	public transient Path catalogFile;

	public Catalog(String baseRef, String description, Path catalogFile) {
		this.baseRef = baseRef;
		this.description = description;
		this.catalogFile = catalogFile;
	}

	public Catalog(String baseRef, String description, Path catalogFile, Map<String, Alias> aliases) {
		this.baseRef = baseRef;
		this.description = description;
		this.catalogFile = catalogFile;
		aliases.entrySet().forEach(e -> {
			Alias a = e.getValue();
			this.aliases.put(e.getKey(), new Alias(a.scriptRef, a.description, a.arguments, a.properties, this));
		});
	}

	/**
	 * Returns in all cases the absolute base reference that can be used to resolve
	 * an Alias' script location. The result will either be a URL or an absolute
	 * path.
	 *
	 * @return A string to be used as the base for Alias script locations
	 */
	public String getScriptBase() {
		Path result;
		if (baseRef != null) {
			if (!AliasUtil.isRemoteRef(baseRef)) {
				Path base = Paths.get(baseRef);
				if (!base.isAbsolute()) {
					result = catalogFile.getParent().resolve(base);
				} else {
					result = Paths.get(baseRef);
				}
			} else {
				if (baseRef.endsWith("/")) {
					return baseRef.substring(0, baseRef.length() - 1);
				} else {
					return baseRef;
				}
			}
		} else {
			result = catalogFile.getParent();
		}
		return result.normalize().toString();
	}
}
