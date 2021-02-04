package dev.jbang.catalog;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import dev.jbang.util.Util;

public class Alias {
	@SerializedName(value = "script-ref", alternate = { "scriptRef" })
	public final String scriptRef;
	public final String description;
	public final List<String> arguments;
	public final Map<String, String> properties;
	public transient Catalog catalog;

	public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties,
			Catalog catalog) {
		this.scriptRef = scriptRef;
		this.description = description;
		this.arguments = arguments;
		this.properties = properties;
		this.catalog = catalog;
	}

	/**
	 * This method returns the scriptRef of the Alias with all contextual modifiers
	 * like baseRefs and current working directories applied.
	 */
	public String resolve(Path cwd) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		String baseRef = catalog.getScriptBase();
		String ref = scriptRef;
		if (!AliasUtil.isAbsoluteRef(ref)) {
			ref = baseRef + "/" + ref;
		}
		if (!AliasUtil.isRemoteRef(ref)) {
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
