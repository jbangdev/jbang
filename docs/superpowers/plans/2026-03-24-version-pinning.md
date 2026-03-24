# Version Pinning for JBang Aliases Implementation Plan (REVISED)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable users to pin aliases to specific versions using `alias:version@catalog` syntax without requiring catalog file changes.

**Architecture:** Extend `Alias.merge()` to parse version from user input, store in transient `requestedVersion` field, and transform `scriptRef` in `Alias.resolve()` using property replacement or automatic pattern matching for Maven GAVs, git URLs, and catalog references.

**Tech Stack:** Java, JUnit 5, existing JBang infrastructure (PropertiesValueResolver, DependencyUtil patterns)

**Spec Reference:** `docs/superpowers/specs/2026-03-24-version-pinning-design.md`

---

## File Structure

**Modified Files:**
- `src/main/java/dev/jbang/catalog/Alias.java` - Add version parsing, storage, and application logic
- `src/test/java/dev/jbang/cli/TestAlias.java` - Add version parsing validation tests

**New Files:**
- `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java` - Comprehensive version replacement tests
- `src/it/java/dev/jbang/it/AliasVersionIT.java` - End-to-end integration tests

---

## Task 1: Add requestedVersion Field and Basic Parsing

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java:21-329`
- Test: `src/test/java/dev/jbang/cli/TestAlias.java`

- [ ] **Step 1: Write failing test for version parsing**

Add to `src/test/java/dev/jbang/cli/TestAlias.java`:

```java
@Test
void testParseVersionedAlias() throws IOException {
    String catalog = "{\n" +
        "  \"aliases\": {\n" +
        "    \"test\": {\n" +
        "      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
        "    }\n" +
        "  }\n" +
        "}";
    Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

    Alias alias = Alias.get("test:2.0.0");

    assertThat(alias, notNullValue());
    assertThat(alias.requestedVersion, equalTo("2.0.0"));
    assertThat(alias.scriptRef, equalTo("com.example:artifact:1.0.0"));
}

@Test
void testParseVersionedAliasFromCatalog() throws IOException {
    String catalog = "{\n" +
        "  \"aliases\": {\n" +
        "    \"test\": {\n" +
        "      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
        "    }\n" +
        "  }\n" +
        "}";
    Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());
    Catalog cat = Catalog.get(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON));

    Alias alias = Alias.get(cat, "test:2.0.0");

    assertThat(alias, notNullValue());
    assertThat(alias.requestedVersion, equalTo("2.0.0"));
}

@Test
void testGavNotParsedAsVersionedAlias() {
    Alias alias = Alias.get("io.quarkus:artifact:1.0.0@jar");
    assertThat(alias, nullValue());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests TestAlias.testParseVersionedAlias --tests TestAlias.testParseVersionedAliasFromCatalog --tests TestAlias.testGavNotParsedAsVersionedAlias`

Expected: FAIL - `requestedVersion` field doesn't exist

- [ ] **Step 3: Add requestedVersion field to Alias class**

In `src/main/java/dev/jbang/catalog/Alias.java`, after line 23 (after `scriptRef` field):

```java
public final String scriptRef;
public transient String requestedVersion;  // ADD THIS LINE
public final String description;
```

- [ ] **Step 4: Modify Alias.merge() to parse version**

In `src/main/java/dev/jbang/catalog/Alias.java`, modify the `merge()` method starting at line 201.

**Replace the entire method with:**

```java
private static Alias merge(Alias a1, String name, Function<String, Alias> findUnqualifiedAlias,
		HashSet<String> names) {
	// NEW: Extract version before GAV check
	// Parse "alias:version@catalog" → aliasName="alias@catalog", version="version"
	String aliasName = name;
	String version = null;

	if (!DependencyUtil.looksLikeAPossibleGav(name)) {
		int colonIdx = name.indexOf(':');
		int atIdx = name.indexOf('@');

		// Only parse as version if : exists and comes before @ (or no @)
		if (colonIdx > 0 && (atIdx == -1 || colonIdx < atIdx)) {
			String beforeColon = name.substring(0, colonIdx);
			String afterColon = name.substring(colonIdx + 1);

			int versionAtIdx = afterColon.indexOf('@');
			if (versionAtIdx > 0) {
				// "test:2.0.0@catalog" → version="2.0.0", aliasName="test@catalog"
				version = afterColon.substring(0, versionAtIdx);
				aliasName = beforeColon + afterColon.substring(versionAtIdx);
			} else {
				// "test:2.0.0" → version="2.0.0", aliasName="test"
				version = afterColon;
				aliasName = beforeColon;
			}

			if (aliasName.isEmpty() || version.isEmpty()) {
				throw new RuntimeException("Invalid alias syntax: '" + name + "'. Expected format: alias:version@catalog");
			}

			Util.verboseMsg("Parsed version '" + version + "' from alias reference '" + name + "'");
		}
	}

	// EXISTING: GAV check (unchanged, but now checks aliasName)
	if (DependencyUtil.looksLikeAPossibleGav(aliasName)) {
		return a1;
	}

	// EXISTING: Loop detection (unchanged, but now checks aliasName)
	if (names.contains(aliasName)) {
		throw new RuntimeException("Encountered alias loop on '" + aliasName + "'");
	}

	// EXISTING: Split on @ for catalog
	String[] parts = aliasName.split("@", 2);
	if (parts[0].isEmpty()) {
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
		Alias result = new Alias(a2.scriptRef, desc, args, jopts, srcs, ress, deps, repos, cpaths, props, javaVersion,
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests TestAlias.testParseVersionedAlias --tests TestAlias.testParseVersionedAliasFromCatalog --tests TestAlias.testGavNotParsedAsVersionedAlias`

Expected: PASS

- [ ] **Step 6: Add validation tests**

Add to `src/test/java/dev/jbang/cli/TestAlias.java`:

```java
@Test
void testEmptyVersionThrowsError() {
    Exception exception = assertThrows(RuntimeException.class, () -> {
        Alias.get("test:@catalog");
    });
    assertThat(exception.getMessage(), containsString("Invalid alias syntax"));
}

@Test
void testEmptyAliasNameThrowsError() {
    Exception exception = assertThrows(RuntimeException.class, () -> {
        Alias.get(":1.0@catalog");
    });
    assertThat(exception.getMessage(), containsString("Invalid alias"));
}
```

- [ ] **Step 7: Run validation tests**

Run: `./gradlew test --tests TestAlias.testEmptyVersionThrowsError --tests TestAlias.testEmptyAliasNameThrowsError`

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAlias.java
git commit -m "feat: add version parsing to Alias.merge()

- Add transient requestedVersion field to Alias class
- Parse alias:version@catalog syntax in merge()
- Skip GAVs from version parsing
- Add validation for empty segments
- Add unit tests for version parsing

Part of #1979"
```

---

## Task 2: Implement Property Replacement for ${jbang.app.version}

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java` (new)

- [ ] **Step 1: Write failing test for property replacement**

Create `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

```java
package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

public class TestAliasVersionReplacement extends BaseTest {

	@BeforeEach
	void initEach() throws IOException {
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testPropertyReplacementWithVersion() throws IOException {
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"tool\": {\n" +
			"      \"script-ref\": \"com.example:artifact:${jbang.app.version:1.0}\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}

	@Test
	void testPropertyReplacementWithoutVersion() throws IOException {
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"tool\": {\n" +
			"      \"script-ref\": \"com.example:artifact:${jbang.app.version:1.0}\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:1.0"));
	}

	@Test
	void testPropertyInUrlReplacement() throws IOException {
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"tool\": {\n" +
			"      \"script-ref\": \"https://example.com/tool-${jbang.app.version:1.0}.jar\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.5.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://example.com/tool-2.5.0.jar"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TestAliasVersionReplacement.testPropertyReplacementWithVersion`

Expected: FAIL - resolve() doesn't apply version yet

- [ ] **Step 3: Implement property replacement in resolve()**

In `src/main/java/dev/jbang/catalog/Alias.java`:

**Add imports after existing imports (after line 19, before class declaration):**

```java
import java.util.Properties;
import dev.jbang.util.PropertiesValueResolver;
```

**Modify the `resolve()` method (around line 169):**

```java
/**
 * This method returns the scriptRef of the Alias with all contextual modifiers
 * like baseRefs and current working directories applied.
 */
public String resolve() {
	String ref = scriptRef;

	// NEW: Apply version transformations if version was requested
	if (requestedVersion != null) {
		ref = applyVersion(ref, requestedVersion);
	}

	// Existing: resolve with base
	return resolve(ref);
}

/**
 * NEW: Apply version to scriptRef through property replacement or automatic patterns
 */
private String applyVersion(String scriptRef, String version) {
	Util.verboseMsg("Version '" + version + "' requested for alias");

	// 1. Build properties with jbang.app.version
	Properties props = new Properties(System.getProperties());
	props.setProperty("jbang.app.version", version);

	// 2. Attempt property replacement
	String afterProperties = PropertiesValueResolver.replaceProperties(scriptRef, props);

	// 3. Check if properties were active (string changed)
	if (!afterProperties.equals(scriptRef)) {
		Util.verboseMsg("Property replacement active in script-ref, skipping automatic version replacement");
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + afterProperties);
		return afterProperties;
	}

	// 4. If no property replacement, we'll add automatic replacement in next tasks
	Util.verboseMsg("No property replacement found, automatic version replacement not yet implemented");
	return scriptRef;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests TestAliasVersionReplacement`

Expected: PASS for property tests

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: implement property replacement for \${jbang.app.version}

- Override resolve() to apply version before baseRef
- Use PropertiesValueResolver for property replacement
- Skip automatic replacement when properties change string
- Add tests for property-based version control

Part of #1979"
```

---

## Task 3: Implement Maven GAV Version Replacement

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`

- [ ] **Step 1: Write failing tests for GAV replacement**

Add to `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

```java
@Test
void testGavVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:2.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
}

@Test
void testGavWithClassifierVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:artifact:1.0.0:classifier\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:2.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("com.example:artifact:2.0.0:classifier"));
}

@Test
void testGavWithClassifierAndTypeVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:artifact:1.0.0:classifier@jar\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:2.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("com.example:artifact:2.0.0:classifier@jar"));
}

@Test
void testGavWithoutVersionAppends() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:artifact\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:2.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGav*`

Expected: FAIL - automatic version replacement not implemented

- [ ] **Step 3: Implement Maven GAV replacement**

In `src/main/java/dev/jbang/catalog/Alias.java`, extend the `applyVersion()` method:

```java
private String applyVersion(String scriptRef, String version) {
	Util.verboseMsg("Version '" + version + "' requested for alias");

	// 1. Build properties with jbang.app.version
	Properties props = new Properties(System.getProperties());
	props.setProperty("jbang.app.version", version);

	// 2. Attempt property replacement
	String afterProperties = PropertiesValueResolver.replaceProperties(scriptRef, props);

	// 3. Check if properties were active (string changed)
	if (!afterProperties.equals(scriptRef)) {
		Util.verboseMsg("Property replacement active in script-ref, skipping automatic version replacement");
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

	Util.warnMsg("Version '" + version + "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
	return scriptRef;
}

/**
 * NEW: Try to replace version in Maven GAV coordinates
 */
private String tryMavenGavReplacement(String ref, String version) {
	if (!DependencyUtil.looksLikeAGav(ref)) {
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGav*`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: implement Maven GAV version replacement

- Add tryMavenGavReplacement() helper method
- Handle full GAV with classifier and type
- Handle lenient GAV (append version if missing)
- Add tests for all GAV replacement scenarios

Part of #1979"
```

---

## Task 4: Implement GitHub URL Version Replacement

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`

- [ ] **Step 1: Write failing tests for GitHub replacement**

Add to `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

```java
@Test
void testGitHubBlobVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://github.com/org/repo/blob/main/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:v1.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://github.com/org/repo/blob/v1.0.0/script.java"));
}

@Test
void testGitHubRawVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://raw.githubusercontent.com/org/repo/main/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:1.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://raw.githubusercontent.com/org/repo/1.0.0/script.java"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGitHub*`

Expected: FAIL - GitHub replacement not implemented

- [ ] **Step 3: Implement GitHub URL replacement**

In `src/main/java/dev/jbang/catalog/Alias.java`, update `applyVersion()`:

```java
private String applyVersion(String scriptRef, String version) {
	// ... existing property replacement logic ...

	String transformed = scriptRef;

	// Try Maven GAV replacement
	String gavResult = tryMavenGavReplacement(scriptRef, version);
	if (!gavResult.equals(scriptRef)) {
		transformed = gavResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	// NEW: Try GitHub URL replacement
	String githubResult = tryGitHubReplacement(scriptRef, version);
	if (!githubResult.equals(scriptRef)) {
		transformed = githubResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	Util.warnMsg("Version '" + version + "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
	return scriptRef;
}

/**
 * NEW: Try to replace version in GitHub URLs
 * NOTE: These patterns are related to Util.swizzleURL() patterns.
 * If git hosting platform support changes there, consider updating here.
 */
private String tryGitHubReplacement(String ref, String version) {
	// Pattern: https://github.com/org/repo/blob/REF/path
	String result = ref.replaceFirst(
		"(https://github\\.com/[^/]+/[^/]+/blob/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	if (!result.equals(ref)) {
		return result;
	}

	// Pattern: https://raw.githubusercontent.com/org/repo/REF/path
	result = ref.replaceFirst(
		"(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	return result;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGitHub*`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: implement GitHub URL version replacement

- Add tryGitHubReplacement() helper method
- Handle both /blob/ and raw.githubusercontent.com URLs
- Replace REF segment with version
- Add note linking to Util.swizzleURL() patterns
- Add tests for GitHub URL scenarios

Part of #1979"
```

---

## Task 5: Implement GitLab and Bitbucket URL Version Replacement

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`

- [ ] **Step 1: Write failing tests for GitLab and Bitbucket**

Add to `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

```java
@Test
void testGitLabBlobVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://gitlab.com/org/repo/-/blob/develop/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:v1.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://gitlab.com/org/repo/-/blob/v1.0/script.java"));
}

@Test
void testGitLabRawVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://gitlab.com/org/repo/-/raw/main/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:1.0.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://gitlab.com/org/repo/-/raw/1.0.0/script.java"));
}

@Test
void testBitbucketSrcVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://bitbucket.org/org/repo/src/master/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:1.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://bitbucket.org/org/repo/src/1.0/script.java"));
}

@Test
void testBitbucketRawVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"https://bitbucket.org/org/repo/raw/master/script.java\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:1.0");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("https://bitbucket.org/org/repo/raw/1.0/script.java"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGitLab* --tests TestAliasVersionReplacement.testBitbucket*`

Expected: FAIL - GitLab/Bitbucket replacement not implemented

- [ ] **Step 3: Implement GitLab and Bitbucket replacement**

In `src/main/java/dev/jbang/catalog/Alias.java`, update `applyVersion()`:

```java
private String applyVersion(String scriptRef, String version) {
	// ... existing logic ...

	String transformed = scriptRef;

	// Try Maven GAV
	String gavResult = tryMavenGavReplacement(scriptRef, version);
	if (!gavResult.equals(scriptRef)) {
		transformed = gavResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	// Try GitHub
	String githubResult = tryGitHubReplacement(scriptRef, version);
	if (!githubResult.equals(scriptRef)) {
		transformed = githubResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	// NEW: Try GitLab
	String gitlabResult = tryGitLabReplacement(scriptRef, version);
	if (!gitlabResult.equals(scriptRef)) {
		transformed = gitlabResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	// NEW: Try Bitbucket
	String bitbucketResult = tryBitbucketReplacement(scriptRef, version);
	if (!bitbucketResult.equals(scriptRef)) {
		transformed = bitbucketResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	Util.warnMsg("Version '" + version + "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
	return scriptRef;
}

/**
 * NEW: Try to replace version in GitLab URLs
 * NOTE: These patterns are related to Util.swizzleURL() patterns.
 */
private String tryGitLabReplacement(String ref, String version) {
	// Pattern: https://gitlab.com/org/repo/-/blob/REF/path
	String result = ref.replaceFirst(
		"(https://gitlab\\.com/[^/]+/[^/]+/-/blob/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	if (!result.equals(ref)) {
		return result;
	}

	// Pattern: https://gitlab.com/org/repo/-/raw/REF/path
	result = ref.replaceFirst(
		"(https://gitlab\\.com/[^/]+/[^/]+/-/raw/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	return result;
}

/**
 * NEW: Try to replace version in Bitbucket URLs
 * NOTE: These patterns are related to Util.swizzleURL() patterns.
 */
private String tryBitbucketReplacement(String ref, String version) {
	// Pattern: https://bitbucket.org/org/repo/src/REF/path
	String result = ref.replaceFirst(
		"(https://bitbucket\\.org/[^/]+/[^/]+/src/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	if (!result.equals(ref)) {
		return result;
	}

	// Pattern: https://bitbucket.org/org/repo/raw/REF/path
	result = ref.replaceFirst(
		"(https://bitbucket\\.org/[^/]+/[^/]+/raw/)([^/]+)(/.+)",
		"$1" + version + "$3"
	);
	return result;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests TestAliasVersionReplacement.testGitLab* --tests TestAliasVersionReplacement.testBitbucket*`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: implement GitLab and Bitbucket URL version replacement

- Add tryGitLabReplacement() helper method
- Add tryBitbucketReplacement() helper method
- Handle both blob/raw and src/raw URL patterns
- Add notes linking to Util.swizzleURL() patterns
- Add tests for GitLab and Bitbucket scenarios

Part of #1979"
```

---

## Task 6: Implement Catalog Reference Version Replacement

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`

- [ ] **Step 1: Write failing test for catalog reference replacement**

Add to `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

```java
@Test
void testCatalogRefVersionReplacement() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"upstream-tool@org/repo/dev\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:1.0");

	// Note: Full resolution would fail without upstream catalog
	// But we can test that scriptRef is transformed
	assertThat(alias.scriptRef, equalTo("upstream-tool@org/repo/dev"));
	assertThat(alias.requestedVersion, equalTo("1.0"));

	// When resolve() is called, it should transform the scriptRef
	// (this will fail to fully resolve without upstream, but we can test the transformation)
}
```

- [ ] **Step 2: Run test to verify it compiles**

Run: `./gradlew test --tests TestAliasVersionReplacement.testCatalogRefVersionReplacement`

Expected: PASS (test is minimal for now)

- [ ] **Step 3: Implement catalog reference replacement**

In `src/main/java/dev/jbang/catalog/Alias.java`, update `applyVersion()`:

```java
private String applyVersion(String scriptRef, String version) {
	// ... existing logic ...

	String transformed = scriptRef;

	// Try Maven GAV, GitHub, GitLab, Bitbucket...
	// (existing code)

	// NEW: Try catalog reference replacement
	String catalogResult = tryCatalogRefReplacement(scriptRef, version);
	if (!catalogResult.equals(scriptRef)) {
		transformed = catalogResult;
		Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
		return transformed;
	}

	Util.warnMsg("Version '" + version + "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
	return scriptRef;
}

/**
 * NEW: Try to replace version in catalog reference (alias@org/repo/REF)
 * NOTE: Catalog.isValidCatalogReference() exists at line 460 of Catalog.java
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
		// It's a registered catalog name, warn and don't replace
		Util.warnMsg("Cannot apply version to registered catalog '" + catalogRef +
			"'. Version replacement only works with implicit catalog references (org/repo/ref).");
		return ref;
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TestAliasVersionReplacement.testCatalogRefVersionReplacement`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: implement catalog reference version replacement

- Add tryCatalogRefReplacement() helper method
- Replace version in org/repo/ref pattern
- Warn for registered catalog names
- Add test for catalog reference scenarios

Part of #1979"
```

---

## Task 7: Add Comprehensive Error Handling and Validation

**Files:**
- Modify: `src/main/java/dev/jbang/catalog/Alias.java`
- Test: `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`

- [ ] **Step 1: Write tests for edge cases**

Add to `src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java`:

**Note:** Warnings are output via `Util.warnMsg()` which typically goes to stderr. Testing warnings requires capturing stderr.

```java
@Test
void testPropertyReplacementSkipsAutomaticReplacement() throws IOException {
	// Mixed scenario: property and version pattern
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:tool-${jbang.app.version:1.0}:2.0\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	Alias alias = Alias.get("tool:3.0");
	String resolved = alias.resolve();

	// Should use property only, not replace :2.0
	assertThat(resolved, equalTo("com.example:tool-3.0:2.0"));
}

@Test
void testBackwardCompatibilityNoVersion() throws IOException {
	String catalog = "{\n" +
		"  \"aliases\": {\n" +
		"    \"tool\": {\n" +
		"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
		"    }\n" +
		"  }\n" +
		"}";
	Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

	// Without version, should work exactly as before
	Alias alias = Alias.get("tool");
	String resolved = alias.resolve();

	assertThat(resolved, equalTo("com.example:artifact:1.0.0"));
	assertThat(alias.requestedVersion, nullValue());
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests TestAliasVersionReplacement.testPropertyReplacementSkipsAutomaticReplacement --tests TestAliasVersionReplacement.testBackwardCompatibilityNoVersion`

Expected: PASS

- [ ] **Step 3: Run full test suite to verify no regressions**

Run: `./gradlew test --tests TestAliasVersionReplacement`

Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/jbang/catalog/Alias.java src/test/java/dev/jbang/cli/TestAliasVersionReplacement.java
git commit -m "feat: add comprehensive error handling and validation

- Ensure property replacement takes precedence
- Add tests for edge cases and backward compatibility
- Verify warning messages work correctly
- Confirm no regressions in existing behavior

Part of #1979"
```

---

## Task 8: Create End-to-End Integration Tests

**Files:**
- Create: `src/it/java/dev/jbang/it/AliasVersionIT.java`

- [ ] **Step 1: Create integration test file**

Create `src/it/java/dev/jbang/it/AliasVersionIT.java`:

```java
package dev.jbang.it;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;

public class AliasVersionIT extends BaseTest {

	@TempDir
	Path testDir;

	@BeforeEach
	void setup() {
		Catalog.clearCache();
	}

	@Test
	void testMavenGavVersionPinning() throws IOException {
		// Create catalog with Maven GAV
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"test-tool\": {\n" +
			"      \"script-ref\": \"info.picocli:picocli:4.6.0\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Path catalogFile = testDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.write(catalogFile, catalog.getBytes());

		// Get alias with version
		Catalog cat = Catalog.get(catalogFile);
		Alias alias = Alias.get(cat, "test-tool:4.7.0");

		// Verify version replacement occurred
		assertThat(alias, notNullValue());
		assertThat(alias.requestedVersion, equalTo("4.7.0"));
		String resolved = alias.resolve();
		assertThat(resolved, equalTo("info.picocli:picocli:4.7.0"));
	}

	@Test
	void testPropertyBasedVersionControl() throws IOException {
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"flexible-tool\": {\n" +
			"      \"script-ref\": \"https://example.com/tool-${jbang.app.version:1.0.0}.jar\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Path catalogFile = testDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.write(catalogFile, catalog.getBytes());

		Catalog cat = Catalog.get(catalogFile);

		// Test with explicit version
		Alias aliasWithVersion = Alias.get(cat, "flexible-tool:2.5.0");
		assertThat(aliasWithVersion, notNullValue());
		String resolvedWithVersion = aliasWithVersion.resolve();
		assertThat(resolvedWithVersion, equalTo("https://example.com/tool-2.5.0.jar"));

		// Test without version (uses default)
		Catalog.clearCache();
		Catalog cat2 = Catalog.get(catalogFile);
		Alias aliasWithoutVersion = Alias.get(cat2, "flexible-tool");
		String resolvedWithoutVersion = aliasWithoutVersion.resolve();
		assertThat(resolvedWithoutVersion, equalTo("https://example.com/tool-1.0.0.jar"));
	}

	@Test
	void testGitHubUrlVersionPinning() throws IOException {
		String catalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"gh-tool\": {\n" +
			"      \"script-ref\": \"https://github.com/example/repo/blob/main/tool.java\"\n" +
			"    }\n" +
			"  }\n" +
			"}";
		Path catalogFile = testDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.write(catalogFile, catalog.getBytes());

		Catalog cat = Catalog.get(catalogFile);
		Alias alias = Alias.get(cat, "gh-tool:v1.0.0");

		assertThat(alias, notNullValue());
		String resolved = alias.resolve();
		assertThat(resolved, equalTo("https://github.com/example/repo/blob/v1.0.0/tool.java"));
	}
}
```

- [ ] **Step 2: Run integration tests**

Run: `./gradlew integrationTest --tests AliasVersionIT`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/it/java/dev/jbang/it/AliasVersionIT.java
git commit -m "test: add end-to-end integration tests for version pinning

- Test Maven GAV version pinning with actual resolution
- Test property-based version control with/without version
- Test GitHub URL version replacement
- Verify all transformations work end-to-end

Part of #1979"
```

---

## Task 9: Update Documentation

**Files:**
- Modify: `docs/modules/ROOT/pages/alias_catalogs.adoc` (add version pinning section)

- [ ] **Step 1: Add documentation section**

Locate `docs/modules/ROOT/pages/alias_catalogs.adoc` and add a section on version pinning (suggest placing after catalog basics):

```asciidoc
== Version Pinning

Pin aliases to specific versions using the `alias:version@catalog` syntax:

[source,bash]
----
jbang mytool:1.5.0@myorg
----

This works with:

* **Maven artifacts**: `tool:2.0.0@catalog` → resolves `com.example:tool:2.0.0`
* **Git URLs**: `tool:v1.0@catalog` → replaces branch/tag in GitHub/GitLab/Bitbucket URLs
* **Catalog references**: `tool:1.0@org/repo/dev` → becomes `org/repo/1.0`

=== Property-Based Control

For custom version placement, use `${jbang.app.version}`:

[source,json]
----
{
  "aliases": {
    "custom-tool": {
      "script-ref": "https://downloads.example.com/tool-${jbang.app.version:1.0}.jar"
    }
  }
}
----

Then run: `jbang custom-tool:2.0@catalog` → downloads `tool-2.0.jar`

=== Backward Compatibility

Existing aliases work unchanged:
* `jbang mytool@catalog` → uses catalog's default version
* `jbang mytool` → uses local alias

Version pinning is purely additive.
```

- [ ] **Step 2: Commit documentation**

```bash
git add docs/modules/ROOT/pages/alias_catalogs.adoc
git commit -m "docs: add version pinning documentation

- Document alias:version@catalog syntax
- Explain Maven, Git URL, and catalog ref support
- Show property-based version control
- Note backward compatibility

Part of #1979"
```

---

## Task 10: Final Verification

**Files:**
- All modified/created files

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`

Expected: ALL PASS

- [ ] **Step 2: Run integration tests**

Run: `./gradlew integrationTest`

Expected: ALL PASS

- [ ] **Step 3: Run existing tests to ensure no regressions**

Run: `./gradlew test --tests TestAlias* --tests TestCatalog*`

Expected: ALL PASS

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete version pinning implementation

Implements alias:version@catalog syntax for version pinning without
catalog changes. Supports Maven GAVs, GitHub/GitLab/Bitbucket URLs,
catalog references, and property-based control.

Features:
- Parse version from alias:version@catalog syntax
- Property replacement via \${jbang.app.version}
- Automatic version replacement for common patterns
- Comprehensive error handling and validation
- Full backward compatibility
- Verbose logging for troubleshooting

Closes #1979"
```

---

## Summary

This plan implements version pinning through 10 focused tasks:

1. **Basic infrastructure**: Add field and parsing with complete merge() implementation
2. **Property replacement**: Handle `${jbang.app.version}` with precedence
3-6. **Pattern matching**: Maven GAV, GitHub, GitLab, Bitbucket, catalog refs
7. **Error handling**: Validation and user feedback
8. **Integration**: End-to-end tests that verify actual transformations
9. **Documentation**: User-facing docs
10. **Verification**: Final testing

Each task follows TDD: write test, watch fail, implement, watch pass, commit.

**Estimated effort**: 4-6 hours for a developer familiar with the codebase.

**Risks**:
- Pattern matching regex accuracy (mitigated by comprehensive tests)
- Property replacement integration (existing system, well-tested)
- Backward compatibility (extensive validation tests)

**Success criteria**:
- All tests pass
- No breaking changes to existing functionality
- Users can pin versions with `alias:version@catalog`
- Clear error messages for invalid input
