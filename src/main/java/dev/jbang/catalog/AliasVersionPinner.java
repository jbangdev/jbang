package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

/**
 * This class has the logic to deal with alias:version[@catalog] syntax.
 * <p>
 * The logic is to apply the version to the script reference in the following
 * order:
 * <ol>
 * <li>Property replacement</li>
 * <li>Maven coordinates (GAV) replacement/appending</li>
 * <li>Versionable URL replacement for known git-hosting URL shapes
 * (GitHub/GitLab/Bitbucket)</li>
 * <li>Catalog reference replacement for {@code alias@org/repo/ref} by swapping
 * the last segment to the requested version</li>
 * </ol>
 */
public final class AliasVersionPinner {
	private AliasVersionPinner() {
	}

	/**
	 * Ordered set of URL patterns that support version substitution.
	 * <p>
	 * The map key is a short provider label used for verbose logging when a
	 * replacement matches (eg. {@code github}, {@code gitlab}, {@code bitbucket}).
	 * <p>
	 * Each {@link Pattern} must contain three capturing groups:
	 * <ol>
	 * <li>prefix up to (and including) the version segment delimiter</li>
	 * <li>the version segment to replace (not used other than for matching)</li>
	 * <li>suffix after the version segment</li>
	 * </ol>
	 * so the replacement can reconstruct {@code group(1) + version + group(3)}.
	 */
	private static final Map<String, List<Pattern>> VERSIONABLE_URL_PATTERNS = new LinkedHashMap<>();
	static {
		VERSIONABLE_URL_PATTERNS.put("github", Arrays.asList(
				Pattern.compile("(https://github\\.com/[^/]+/[^/]+/blob/)([^/]+)(/.+)"),
				Pattern.compile("(https://github\\.com/[^/]+/[^/]+/releases/download/)([^/]+)(/.+)"),
				Pattern.compile("(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/)([^/]+)(/.+)")));
		VERSIONABLE_URL_PATTERNS.put("gitlab", Arrays.asList(
				Pattern.compile("(https://gitlab\\.com/[^/]+/[^/]+/-/blob/)([^/]+)(/.+)"),
				Pattern.compile("(https://gitlab\\.com/[^/]+/[^/]+/-/raw/)([^/]+)(/.+)")));
		VERSIONABLE_URL_PATTERNS.put("bitbucket", Arrays.asList(
				Pattern.compile("(https://bitbucket\\.org/[^/]+/[^/]+/src/)([^/]+)(/.+)"),
				Pattern.compile("(https://bitbucket\\.org/[^/]+/[^/]+/raw/)([^/]+)(/.+)")));
	}

	/**
	 * Apply an inline version to a {@code scriptRef} using a fixed replacement
	 * cascade.
	 * <p>
	 * Cascade order:
	 * <ol>
	 * <li><b>Property replacement</b> using {@code jbang.app.version}. If any
	 * property replacement occurs, automatic replacement is skipped.</li>
	 * <li><b>Maven coordinates (GAV)</b> replacement/appending.</li>
	 * <li><b>Versionable URL</b> replacement for known git-hosting URL shapes
	 * (GitHub/GitLab/Bitbucket).</li>
	 * <li><b>Catalog reference</b> replacement for {@code alias@org/repo/ref} by
	 * swapping the last segment to the requested version.</li>
	 * </ol>
	 * If none apply, an {@link ExitException} is thrown for least surprise.
	 *
	 * @param scriptRef the original script reference from an alias definition
	 * @param version   the requested version (non-null)
	 * @return the transformed script reference
	 */
	public static String applyVersion(String scriptRef, String version) {
		Properties props = ProjectBuilder.getContextProperties(Collections.emptyMap());
		return applyVersion(scriptRef, version, props);
	}

	public static String applyVersion(String scriptRef, String version, Properties baseProperties) {
		if (version == null) {
			return scriptRef;
		}

		Util.verboseMsg("Version '" + version + "' requested for alias");

		Properties props = new Properties();
		if (baseProperties != null) {
			props.putAll(baseProperties);
		}
		props.setProperty("jbang.app.version", version);

		String afterProperties = PropertiesValueResolver.replaceProperties(scriptRef, props);
		if (!afterProperties.equals(scriptRef)) {
			Util.verboseMsg("Property replacement active in script-ref, skipping automatic version replacement");
			Util.verboseMsg("Version pinned property replacement: " + scriptRef + " → " + afterProperties);
			return afterProperties;
		}

		Util.verboseMsg("Applying automatic version replacement to: " + scriptRef);

		String gavResult = tryMavenGavReplacement(scriptRef, version);
		if (!gavResult.equals(scriptRef)) {
			Util.verboseMsg("Version pinned Maven GAV: " + scriptRef + " → " + gavResult);
			return gavResult;
		}

		Replacement urlReplacement = tryVersionableUrlReplacement(scriptRef, version);
		if (urlReplacement != null) {
			Util.verboseMsg("Matched versionable URL pattern: " + urlReplacement.key);
			Util.verboseMsg("Version pinned URL: " + scriptRef + " → " + urlReplacement.replaced);
			return urlReplacement.replaced;
		}

		String catalogResult = tryCatalogRefReplacement(scriptRef, version);
		if (!catalogResult.equals(scriptRef)) {
			Util.verboseMsg("Version pinned catalog reference: " + scriptRef + " → " + catalogResult);
			return catalogResult;
		}

		if (Catalog.isValidCatalogReference(scriptRef)) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Cannot apply version '" + version + "' to catalog reference '" + scriptRef
							+ "'. Registered catalog names don't support version pinning. "
							+ "Either use a path-based catalog (e.g., alias@org/repo/ref) or have the alias define ${jbang.app.version} in its script-ref.");
		} else {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Cannot apply version '" + version + "' to script-ref '" + scriptRef
							+ "'. No recognizable version pattern found. "
							+ "Supported patterns: Maven GAV, GitHub/GitLab/Bitbucket URLs, or use ${jbang.app.version:default} in the alias definition.");
		}
	}

	/**
	 * Attempt to apply a version to Maven coordinates.
	 * <p>
	 * Supports:
	 * <ul>
	 * <li>Full GAV: {@code group:artifact:version[:classifier][@type]} (replaces
	 * the version)</li>
	 * <li>Lenient GAV: {@code group:artifact} (appends {@code :version})</li>
	 * </ul>
	 *
	 * @param ref     input reference
	 * @param version requested version
	 * @return transformed reference if a Maven-like pattern matched, otherwise
	 *         {@code ref}
	 */
	private static String tryMavenGavReplacement(String ref, String version) {
		if (!DependencyUtil.looksLikeAPossibleGav(ref)) {
			return ref;
		}

		Matcher m = DependencyUtil.fullGavPattern.matcher(ref);
		if (m.matches()) {
			String g = m.group("groupid");
			String a = m.group("artifactid");
			String c = m.group("classifier");
			String t = m.group("type");

			StringBuilder result = new StringBuilder(g).append(":").append(a).append(":").append(version);
			if (c != null && !c.isEmpty()) {
				result.append(":").append(c);
			}
			if (t != null && !t.isEmpty()) {
				result.append("@").append(t);
			}
			return result.toString();
		}

		m = DependencyUtil.lenientGavPattern.matcher(ref);
		if (m.matches() && m.group("version") == null) {
			return m.group("groupid") + ":" + m.group("artifactid") + ":" + version;
		}

		return ref;
	}

	/**
	 * Small carrier for a successful replacement.
	 */
	private static final class Replacement {
		final String key;
		final String replaced;

		private Replacement(String key, String replaced) {
			this.key = key;
			this.replaced = replaced;
		}
	}

	/**
	 * Attempt to apply a version to a known versionable URL pattern.
	 * <p>
	 * Patterns are tried in the insertion order of
	 * {@link #VERSIONABLE_URL_PATTERNS}.
	 *
	 * @param ref     input reference
	 * @param version requested version
	 * @return a {@link Replacement} containing the provider key and replaced value,
	 *         or {@code null}
	 */
	private static Replacement tryVersionableUrlReplacement(String ref, String version) {
		for (Map.Entry<String, List<Pattern>> entry : VERSIONABLE_URL_PATTERNS.entrySet()) {
			String key = entry.getKey();
			for (Pattern p : entry.getValue()) {
				Matcher m = p.matcher(ref);
				if (m.matches()) {
					String replaced = m.group(1) + version + m.group(3);
					return new Replacement(key, replaced);
				}
			}
		}
		return null;
	}

	/**
	 * Attempt to apply a version to a catalog reference in the form:
	 * {@code alias@org/repo/ref}.
	 * <p>
	 * Only path-based catalog references are versionable; registered catalog names
	 * (no slash segments) are left untouched and will be rejected by the caller's
	 * fallback error.
	 *
	 * @param ref     input reference
	 * @param version requested version
	 * @return transformed reference if a path-based catalog reference matched,
	 *         otherwise {@code ref}
	 */
	private static String tryCatalogRefReplacement(String ref, String version) {
		if (!Catalog.isValidCatalogReference(ref)) {
			return ref;
		}

		String[] parts = ref.split("@", 2);
		if (parts.length != 2) {
			return ref;
		}

		String aliasName = parts[0];
		String catalogRef = parts[1];

		int lastSlash = catalogRef.lastIndexOf('/');
		if (lastSlash > 0 && lastSlash < catalogRef.length() - 1) {
			String catalogBase = catalogRef.substring(0, lastSlash);
			return aliasName + "@" + catalogBase + "/" + version;
		}
		return ref;
	}
}
