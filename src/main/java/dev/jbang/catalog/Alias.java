package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

public class Alias extends CatalogItem {
	@SerializedName(value = "script-ref", alternate = { "scriptRef" })
	public final String scriptRef;
	/**
	 * Version requested by user via alias:version syntax. Transient because it's
	 * not stored in catalog files - only used at runtime for version resolution.
	 */
	public transient String requestedVersion;
	public final String description;
	public final List<String> arguments;
	@SerializedName(value = "runtime-options", alternate = { "java-options" })
	public final List<String> runtimeOptions;
	public final List<String> sources;
	@SerializedName(value = "files")
	public final List<String> resources;
	public final List<String> dependencies;
	public final List<String> repositories;
	public final List<String> classpaths;
	public final Map<String, String> properties;
	@SerializedName(value = "java")
	public final String javaVersion;
	@SerializedName(value = "main")
	public final String mainClass;
	@SerializedName(value = "module")
	public final String moduleName;
	@SerializedName(value = "compile-options")
	public final List<String> compileOptions;
	@SerializedName(value = "native-image")
	public final Boolean nativeImage;
	@SerializedName(value = "native-options")
	public final List<String> nativeOptions;
	@SerializedName(value = "source-type")
	public final String forceType;
	public final Boolean integrations;
	public final String jfr;
	public final Map<String, String> debug;
	public final Boolean cds;
	public final Boolean interactive;
	@SerializedName(value = "enable-preview")
	public final Boolean enablePreview;
	@SerializedName(value = "enable-assertions")
	public final Boolean enableAssertions;
	@SerializedName(value = "enable-system-assertions")
	public final Boolean enableSystemAssertions;
	@SerializedName(value = "manifest-options")
	public final Map<String, String> manifestOptions;
	@SerializedName(value = "java-agents")
	@JsonAdapter(Catalog.SkipEmptyListSerializer.class)
	public final List<JavaAgent> javaAgents;
	@JsonAdapter(Catalog.SkipEmptyListSerializer.class)
	public final List<String> docs;

	public static class JavaAgent {
		@SerializedName(value = "agent-ref")
		@NonNull
		public final String agentRef;
		@NonNull
		public final String options;

		public JavaAgent() { // to make gson happy in native image
			this(null, null);
		}

		public JavaAgent(@NonNull String agentRef, @NonNull String options) {
			this.agentRef = agentRef;
			this.options = options;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			JavaAgent javaAgent = (JavaAgent) o;
			return agentRef.equals(javaAgent.agentRef) && options.equals(javaAgent.options);
		}

		@Override
		public int hashCode() {
			return Objects.hash(agentRef, options);
		}
	}

	public Alias() {
		this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
	}

	public Alias(String scriptRef,
			String description,
			List<String> arguments,
			List<String> runtimeOptions,
			List<String> sources,
			List<String> resources,
			List<String> dependencies,
			List<String> repositories,
			List<String> classpaths,
			Map<String, String> properties,
			String javaVersion,
			String mainClass,
			String moduleName,
			List<String> compileOptions,
			Boolean nativeImage,
			List<String> nativeOptions,
			String forceType,
			Boolean integrations,
			String jfr,
			Map<String, String> debug,
			Boolean cds,
			Boolean interactive,
			Boolean enablePreview,
			Boolean enableAssertions,
			Boolean enableSystemAssertions,
			Map<String, String> manifestOptions,
			List<JavaAgent> javaAgents,
			List<String> docs,
			Catalog catalog) {
		super(catalog);
		this.scriptRef = scriptRef;
		this.description = description;
		this.arguments = arguments;
		this.runtimeOptions = runtimeOptions;
		this.sources = sources;
		this.resources = resources;
		this.dependencies = dependencies;
		this.repositories = repositories;
		this.classpaths = classpaths;
		this.properties = properties;
		this.javaVersion = javaVersion;
		this.mainClass = mainClass;
		this.moduleName = moduleName;
		this.compileOptions = compileOptions;
		this.nativeImage = nativeImage;
		this.nativeOptions = nativeOptions;
		this.forceType = forceType;
		this.integrations = integrations;
		this.jfr = jfr;
		this.debug = debug;
		this.cds = cds;
		this.interactive = interactive;
		this.enablePreview = enablePreview;
		this.enableAssertions = enableAssertions;
		this.enableSystemAssertions = enableSystemAssertions;
		this.manifestOptions = manifestOptions;
		this.javaAgents = javaAgents;
		this.docs = docs;
	}

	/**
	 * This method returns the scriptRef of the Alias with all contextual modifiers
	 * like baseRefs and current working directories applied.
	 */
	public String resolve() {
		String ref = scriptRef;

		// Apply version transformations if version was requested
		if (requestedVersion != null) {
			ref = applyVersion(ref, requestedVersion);
		} else {
			// Still resolve properties using system properties (for defaults)
			ref = PropertiesValueResolver.replaceProperties(ref);
		}

		return resolve(ref);
	}

	/**
	 * Apply version to scriptRef through property replacement or automatic patterns
	 */
	private String applyVersion(String scriptRef, String version) {
		Util.verboseMsg("Version '" + version + "' requested for alias");

		// 1. Build properties with jbang.app.version
		// Use getContextProperties to include system properties, Detector platform
		// properties,
		// and any user-provided properties (e.g., from -D flags)
		Properties props = ProjectBuilder.getContextProperties(Collections.emptyMap());
		props.setProperty("jbang.app.version", version);

		// 2. Attempt property replacement
		String afterProperties = PropertiesValueResolver.replaceProperties(scriptRef, props);

		// 3. Check if properties were active (string changed)
		if (!afterProperties.equals(scriptRef)) {
			Util.verboseMsg(
					"Property replacement active in script-ref, skipping automatic version replacement");
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + afterProperties);
			return afterProperties;
		}

		// 4. No properties were replaced, apply automatic version replacement
		Util.verboseMsg("Applying automatic version replacement to: " + scriptRef);

		String transformed = scriptRef;

		// Try Maven GAV replacement
		String gavResult = tryMavenGavReplacement(scriptRef, version);
		if (!gavResult.equals(scriptRef)) {
			transformed = gavResult;
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
			return transformed;
		}

		// Try GitHub URL replacement
		String githubResult = tryGitHubReplacement(scriptRef, version);
		if (!githubResult.equals(scriptRef)) {
			transformed = githubResult;
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
			return transformed;
		}

		// Try GitLab URL replacement
		String gitlabResult = tryGitLabReplacement(scriptRef, version);
		if (!gitlabResult.equals(scriptRef)) {
			transformed = gitlabResult;
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
			return transformed;
		}

		// Try Bitbucket URL replacement
		String bitbucketResult = tryBitbucketReplacement(scriptRef, version);
		if (!bitbucketResult.equals(scriptRef)) {
			transformed = bitbucketResult;
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
			return transformed;
		}

		// Try catalog reference replacement
		String catalogResult = tryCatalogRefReplacement(scriptRef, version);
		if (!catalogResult.equals(scriptRef)) {
			transformed = catalogResult;
			Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
			return transformed;
		}

		Util.warnMsg("Version '" + version
				+ "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
		return scriptRef;
	}

	/**
	 * Try to replace version in Maven GAV coordinates
	 */
	private String tryMavenGavReplacement(String ref, String version) {
		if (!DependencyUtil.looksLikeAPossibleGav(ref)) {
			return ref;
		}

		java.util.regex.Matcher m = DependencyUtil.fullGavPattern.matcher(ref);
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

		// Try lenient pattern (no version present)
		m = DependencyUtil.lenientGavPattern.matcher(ref);
		if (m.matches() && m.group("version") == null) {
			return m.group("groupid") + ":" + m.group("artifactid") + ":" + version;
		}

		return ref;
	}

	/**
	 * Try to replace version in GitHub URLs NOTE: These patterns are related to
	 * Util.swizzleURL() patterns. If git hosting platform support changes there,
	 * consider updating here.
	 */
	private String tryGitHubReplacement(String ref, String version) {
		// Pattern: https://github.com/org/repo/blob/REF/path
		String result = ref.replaceFirst(
				"(https://github\\.com/[^/]+/[^/]+/blob/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		if (!result.equals(ref)) {
			return result;
		}

		// Pattern: https://raw.githubusercontent.com/org/repo/REF/path
		result = ref.replaceFirst(
				"(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		return result;
	}

	/**
	 * Try to replace version in GitLab URLs
	 */
	private String tryGitLabReplacement(String ref, String version) {
		// Pattern: https://gitlab.com/org/repo/-/blob/REF/path
		String result = ref.replaceFirst(
				"(https://gitlab\\.com/[^/]+/[^/]+/-/blob/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		if (!result.equals(ref)) {
			return result;
		}

		// Pattern: https://gitlab.com/org/repo/-/raw/REF/path
		result = ref.replaceFirst(
				"(https://gitlab\\.com/[^/]+/[^/]+/-/raw/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		return result;
	}

	/**
	 * Try to replace version in Bitbucket URLs
	 */
	private String tryBitbucketReplacement(String ref, String version) {
		// Pattern: https://bitbucket.org/org/repo/src/REF/path
		String result = ref.replaceFirst(
				"(https://bitbucket\\.org/[^/]+/[^/]+/src/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		if (!result.equals(ref)) {
			return result;
		}

		// Pattern: https://bitbucket.org/org/repo/raw/REF/path
		result = ref.replaceFirst(
				"(https://bitbucket\\.org/[^/]+/[^/]+/raw/)([^/]+)(/.+)",
				"$1" + java.util.regex.Matcher.quoteReplacement(version) + "$3");
		return result;
	}

	/**
	 * Try to replace version in catalog references (alias@org/repo/REF)
	 */
	private String tryCatalogRefReplacement(String ref, String version) {
		if (!Catalog.isValidCatalogReference(ref)) {
			return ref;
		}

		// Pattern: alias@org/repo/REF
		String[] parts = ref.split("@", 2);
		if (parts.length != 2) {
			return ref;
		}

		String aliasName = parts[0];
		String catalogRef = parts[1];

		// Check if catalogRef has path segments (org/repo/ref)
		int lastSlash = catalogRef.lastIndexOf('/');
		if (lastSlash > 0 && lastSlash < catalogRef.length() - 1) {
			// Replace the last segment with version
			String catalogBase = catalogRef.substring(0, lastSlash);
			return aliasName + "@" + catalogBase + "/" + version;
		} else {
			// It's a registered catalog name, can't apply version
			return ref;
		}
	}

	/**
	 * Returns an Alias object for the given name
	 *
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(String aliasName) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias();
		Alias result = merge(alias, aliasName, Alias::getLocal, names);
		return result.scriptRef != null ? result : null;
	}

	/**
	 * Returns an Alias object for the given name. The given Catalog will be used
	 * for any unqualified alias lookups.
	 *
	 * @param catalog   A Catalog to use for lookups
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias get(Catalog catalog, String aliasName) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias();
		Alias result = merge(alias, aliasName, catalog.aliases::get, names);
		return result.scriptRef != null ? result : null;
	}

	private static Alias merge(Alias a1, String name, Function<String, Alias> findUnqualifiedAlias,
			HashSet<String> names) {
		// Validate alias name format early
		if (name.startsWith(":") || name.startsWith("@")) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}

		// Distinguish between GAV coordinates and alias:version syntax
		// This logic precedes looksLikeAPossibleGav() because that method's lenient
		// pattern
		// would match "alias:version" as "group:artifact" (e.g., "one:2.0.0" →
		// group=one, artifact=2.0.0)
		//
		// Strategy: Count colons before @ to determine format
		// - "group:artifact:version" has 2+ colons → GAV
		// - "alias:version@catalog" has 1 colon → alias with version
		// - Additionally check for dots in first segment (typical of Maven groupIds)
		int colonIdx = name.indexOf(':');
		if (colonIdx > 0) {
			int atIdx = name.indexOf('@');
			String beforeAt = atIdx >= 0 ? name.substring(0, atIdx) : name;

			// Count colons before @
			int colonCount = 0;
			for (char c : beforeAt.toCharArray()) {
				if (c == ':')
					colonCount++;
			}

			// 2+ colons before @ → definitely a GAV (group:artifact:version...)
			if (colonCount >= 2) {
				return a1;
			}

			// 1 colon: could be "alias:version" or "group.id:artifact"
			// Check if first segment looks like a Maven groupId (contains dot)
			String beforeFirstColon = name.substring(0, colonIdx);
			if (beforeFirstColon.contains(".")) {
				// Likely a GAV like "com.example:artifact" or "org.test:lib:1.0"
				return a1;
			}
		}

		// Parse potential "alias:version@catalog" → aliasName="alias@catalog",
		// version="version"
		String aliasName = name;
		String version = null;

		// Try to parse as alias:version if : exists and comes before @ (or no @)
		int atIdx = name.indexOf('@');
		if (colonIdx > 0 && (atIdx == -1 || colonIdx < atIdx)) {
			String beforeColon = name.substring(0, colonIdx);
			String afterColon = name.substring(colonIdx + 1);

			int versionAtIdx = afterColon.indexOf('@');
			if (versionAtIdx == 0) {
				// "test:@catalog" → empty version, error
				throw new RuntimeException(
						"Invalid alias syntax: '" + name + "'. Expected format: alias:version@catalog");
			} else if (versionAtIdx > 0) {
				// "test:2.0.0@catalog" → version="2.0.0", aliasName="test@catalog"
				version = afterColon.substring(0, versionAtIdx);
				aliasName = beforeColon + afterColon.substring(versionAtIdx);
			} else {
				// "test:2.0.0" → version="2.0.0", aliasName="test"
				version = afterColon;
				aliasName = beforeColon;
			}

			if (aliasName.isEmpty() || version.isEmpty()) {
				throw new RuntimeException(
						"Invalid alias syntax: '" + name + "'. Expected format: alias:version@catalog");
			}

			Util.verboseMsg("Parsed version '" + version + "' from alias reference '" + name + "'");
		}

		// Final GAV check on the parsed aliasName (after version removal)
		if (DependencyUtil.looksLikeAPossibleGav(aliasName)) {
			return a1;
		}

		// EXISTING: Loop detection (unchanged, but now checks aliasName)
		if (names.contains(aliasName)) {
			throw new RuntimeException("Encountered alias loop on '" + aliasName + "'");
		}

		// EXISTING: Split on @ for catalog
		String[] parts = aliasName.split("@", 2);
		if (parts[0].isEmpty() || parts[0].startsWith(":")) {
			throw new RuntimeException("Invalid alias name '" + aliasName + "'");
		}

		// EXISTING: Look up alias (unchanged)
		Alias a2;
		if (parts.length == 1) {
			a2 = findUnqualifiedAlias.apply(aliasName);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + aliasName + "'");
			}
			a2 = fromCatalog(parts[1], parts[0]);
		}

		// EXISTING: Merge if found
		if (a2 != null) {
			names.add(aliasName);
			a2 = merge(a2, a2.scriptRef, findUnqualifiedAlias, names);

			// EXISTING: Property merging (all unchanged from original code)
			String desc = a1.description != null ? a1.description : a2.description;
			List<String> args = a1.arguments != null && !a1.arguments.isEmpty() ? a1.arguments : a2.arguments;
			List<String> jopts = a1.runtimeOptions != null && !a1.runtimeOptions.isEmpty() ? a1.runtimeOptions
					: a2.runtimeOptions;
			List<String> srcs = a1.sources != null && !a1.sources.isEmpty() ? a1.sources
					: a2.sources;
			List<String> ress = a1.resources != null && !a1.resources.isEmpty() ? a1.resources
					: a2.resources;
			List<String> deps = a1.dependencies != null && !a1.dependencies.isEmpty() ? a1.dependencies
					: a2.dependencies;
			List<String> repos = a1.repositories != null && !a1.repositories.isEmpty() ? a1.repositories
					: a2.repositories;
			List<String> cpaths = a1.classpaths != null && !a1.classpaths.isEmpty() ? a1.classpaths
					: a2.classpaths;
			Map<String, String> props = a1.properties != null && !a1.properties.isEmpty() ? a1.properties
					: a2.properties;
			String javaVersion = a1.javaVersion != null ? a1.javaVersion : a2.javaVersion;
			String mainClass = a1.mainClass != null ? a1.mainClass : a2.mainClass;
			String moduleName = a1.moduleName != null ? a1.moduleName : a2.moduleName;
			List<String> copts = a1.compileOptions != null && !a1.compileOptions.isEmpty() ? a1.compileOptions
					: a2.compileOptions;
			List<String> nopts = a1.nativeOptions != null && !a1.nativeOptions.isEmpty() ? a1.nativeOptions
					: a2.nativeOptions;
			String forceType = a1.forceType != null ? a1.forceType : a2.forceType;
			Boolean nimg = a1.nativeImage != null ? a1.nativeImage : a2.nativeImage;
			Boolean ints = a1.integrations != null ? a1.integrations : a2.integrations;
			String jfr = a1.jfr != null ? a1.jfr : a2.jfr;
			Map<String, String> debug = a1.debug != null ? a1.debug : a2.debug;
			Boolean cds = a1.cds != null ? a1.cds : a2.cds;
			Boolean inter = a1.interactive != null ? a1.interactive : a2.interactive;
			Boolean ep = a1.enablePreview != null ? a1.enablePreview : a2.enablePreview;
			Boolean ea = a1.enableAssertions != null ? a1.enableAssertions : a2.enableAssertions;
			Boolean esa = a1.enableSystemAssertions != null ? a1.enableSystemAssertions : a2.enableSystemAssertions;
			Map<String, String> mopts = a1.manifestOptions != null && !a1.manifestOptions.isEmpty() ? a1.manifestOptions
					: a2.manifestOptions;
			List<JavaAgent> jags = a1.javaAgents != null && !a1.javaAgents.isEmpty() ? a1.javaAgents : a2.javaAgents;
			List<String> docs = a1.docs != null && !a1.docs.isEmpty() ? a1.docs : a2.docs;
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;

			// EXISTING: Create merged alias
			Alias result = new Alias(a2.scriptRef, desc, args, jopts, srcs, ress, deps, repos, cpaths, props,
					javaVersion,
					mainClass, moduleName, copts, nimg, nopts, forceType, ints, jfr, debug, cds, inter, ep, ea, esa,
					mopts, jags, docs, catalog);

			// NEW: Set requestedVersion if version was parsed
			if (version != null) {
				result.requestedVersion = version;
			}

			return result;
		} else {
			return a1;
		}
	}

	/**
	 * Returns the given Alias from the local file system
	 *
	 * @param aliasName The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getLocal(String aliasName) {
		Catalog catalog = findNearestCatalogWithAlias(Util.getCwd(), aliasName);
		if (catalog != null) {
			return catalog.aliases.getOrDefault(aliasName, null);
		}
		return null;
	}

	static Catalog findNearestCatalogWithAlias(Path dir, String aliasName) {
		return Catalog.findNearestCatalogWith(dir, true, true,
				catalog -> catalog.aliases.containsKey(aliasName) ? catalog : null);
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 *
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias fromCatalog(String catalogName, String aliasName) {
		Catalog catalog = Catalog.getByName(catalogName);
		Alias alias = catalog.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No alias found with name '" + aliasName + "'");
		}
		return alias;
	}

	public Alias withCatalog(Catalog catalog) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}

	public Alias withScriptRef(String scriptRef) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}

	public Alias withForceType(String forceType) {
		return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources, dependencies,
				repositories, classpaths, properties, javaVersion, mainClass, moduleName, compileOptions, nativeImage,
				nativeOptions, forceType, integrations, jfr, debug, cds, interactive, enablePreview, enableAssertions,
				enableSystemAssertions, manifestOptions, javaAgents, docs, catalog);
	}
}
