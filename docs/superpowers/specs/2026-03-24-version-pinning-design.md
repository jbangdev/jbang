# Version Pinning for JBang Aliases - Design Specification

**Date:** 2026-03-24
**Issue:** [#1979](https://github.com/jbangdev/jbang/issues/1979)
**Status:** Approved

## Overview

This design implements version pinning for JBang aliases, enabling users to lock specific versions of scripts/tools from remote catalogs. The syntax `alias:version@catalog` extends the existing `alias@catalog` pattern without requiring any catalog file changes.

### Problem Statement

Users running aliases from remote catalogs face five key challenges:

1. **Automatic vs. manual updates** - Whether to automatically update scripts (risking breakage) or avoid updates (missing security fixes)
2. **Version pinning** - Need ability to lock specific versions they've tested
3. **Update notifications** - How to alert users about newer available versions (deferred)
4. **Multiple version support** - Managing and installing specific versions of scripts
5. **Version discovery** - Enabling users to list available versions (deferred)

### Solution

Extend alias syntax to support version specification: `alias:version@catalog`. The version information transforms the resolved `script-ref` before execution, leveraging existing versioning systems (Maven repositories, git tags/branches) without requiring catalog metadata.

## Architecture Overview

**Core Concept:**
Version information flows through the alias resolution pipeline and transforms the resolved `script-ref` before execution.

**Key Components:**

1. **Syntax Parsing** - Extend `Alias.merge()` to extract version from `alias:version@catalog`
2. **Version Storage** - Add transient `requestedVersion` field to `Alias` (not persisted to catalog JSON)
3. **Version Application** - Extend `Alias.resolve()` to apply version transformations
4. **Property Support** - Handle `${jbang.app.version:default}` for custom version placement

**Control Flow:**

```
User invocation: jbang mytool:1.5.0@myorg
         ↓
    Parse syntax (merge)
         ↓
   Load alias from catalog
         ↓
    Store version in Alias
         ↓
    resolve() transforms scriptRef
         ↓
    Execute with versioned ref
```

**Design Principles:**

- **Zero catalog changes** - Existing catalogs work unchanged, no version metadata stored
- **Backward compatible** - `alias@catalog` continues to work exactly as before
- **User control** - `${jbang.app.version}` property for manual version placement
- **Fail safe** - Error when version can't be applied, warn for ambiguous cases

## Syntax Parsing and Validation

### Parsing Logic in `Alias.merge()`

Current parsing: `name.split("@", 2)` → `[alias, catalog]`

New parsing:
1. Split on `"@"` → separates alias+version from catalog
2. Split first part on `":"` → separates alias from version
3. Validate patterns

### Validation Rules

**Pattern `alias:version@catalog`:**
- `:` can only appear once in the alias portion (before `@`)
- Version part can be any string (git tags, semver, etc.)
- Catalog portion unchanged from existing logic

**Distinguish from Maven GAV:**
- Use existing `DependencyUtil.looksLikeAPossibleGav()` check BEFORE parsing for version
- If it looks like GAV, skip version parsing entirely
- Example: `io.quarkus:artifact:1.0@jar` → treated as GAV, not versioned alias

**Empty segments error:**
- `mytool:@catalog` → Error (empty version)
- `:1.0@catalog` → Error (empty alias name)
- `mytool:1.0@` → Error (empty catalog, existing validation)

**Local aliases with version:**
- `mytool:1.0` (no `@catalog`) → Warning: "Version specified but no catalog. Version pinning only works with catalog references."
- Still try to apply version to local alias scriptRef

**Invalid patterns:**
- `mytool:v1:v2@catalog` → Error: "Invalid syntax. Use alias:version@catalog"

### Storage

Add to `Alias` class:
- `public transient String requestedVersion`
- Transient = not serialized to JSON
- Populated during `merge()`, flows through alias chain
- `null` when no version specified (backward compatible)

## Version Application Logic

### Core Rule in `Alias.resolve()`

```java
if (property replacement changed scriptRef) {
    → Use property-replaced value
    → Skip automatic version replacement
} else if (requestedVersion != null) {
    → Apply automatic version replacement
    → Then apply baseRef (existing logic)
} else {
    → No version replacement (existing behavior)
}
```

### Property Replacement Priority

**Rule:** If property replacement changes the `scriptRef`, automatic version replacement is skipped. User has chosen manual control.

**Detection:** Compare `scriptRef` before and after property replacement. If strings differ, properties were active.

**Benefits:**
- Works with any property syntax (current or future)
- Simple logic - just compare strings
- No automatic magic when user has explicit control

### Property Replacement (`${jbang.app.version:default}`)

- Scan `scriptRef` for `${jbang.app.version:default}` pattern
- If `requestedVersion != null`, replace with `requestedVersion`
- If `requestedVersion == null`, replace with `default` value
- Can appear multiple times in same string (all replaced with same value)
- Integrated with existing `PropertiesValueResolver` system

### Automatic Version Replacement

Applied when no property replacement occurred and `requestedVersion != null`.

**Type 1: Maven GAV**

Pattern: `groupId:artifactId:version[:classifier][@type]`

Examples:
- `com.example:tool:1.0` → `com.example:tool:2.0`
- `com.example:tool:1.0:classifier@jar` → `com.example:tool:2.0:classifier@jar`
- `com.example:tool` → `com.example:tool:2.0` (append version)

Implementation: Use existing `DependencyUtil.fullGavPattern` and `lenientGavPattern`

**Type 2: GitHub URL**

Patterns:
- `https://github.com/org/repo/blob/REF/path`
- `https://raw.githubusercontent.com/org/repo/REF/path`

Replace REF segment with `requestedVersion`

Example: `github.com/org/repo/blob/main/script.java` → `github.com/org/repo/blob/1.0/script.java`

**Type 3: GitLab URL**

Patterns:
- `https://gitlab.com/org/repo/-/blob/REF/path`
- `https://gitlab.com/org/repo/-/raw/REF/path`

Replace REF segment

Example: `gitlab.com/org/repo/-/blob/develop/script.java` → `gitlab.com/org/repo/-/blob/v1.0/script.java`

**Type 4: Bitbucket URL**

Patterns:
- `https://bitbucket.org/org/repo/src/REF/path`
- `https://bitbucket.org/org/repo/raw/REF/path`

Replace REF segment

Example: `bitbucket.org/org/repo/src/master/script.java` → `bitbucket.org/org/repo/src/1.0/script.java`

**Type 5: Catalog Reference**

Pattern: `alias-name@org/repo/REF`

Replace REF with `requestedVersion`

Example: `mytool@upstream/dev` → `mytool@upstream/1.0`

Note: Registered catalog names (no `/`) → Warning: "Cannot apply version to registered catalog reference"

**Type 6: Other (file paths, other URLs, etc.)**

Warning: "Version specified but script-ref has no recognizable version pattern: {scriptRef}"

Use original `scriptRef` unchanged

## Implementation Details and Data Flow

### Changes to `Alias` Class

```java
public class Alias extends CatalogItem {
    // Existing fields...
    public final String scriptRef;

    // NEW: Transient field for runtime version
    public transient String requestedVersion;

    // Existing constructor stays same...
}
```

### Changes to `Alias.merge()` Method

```java
private static Alias merge(Alias a1, String name, ...) {
    // NEW: Extract version before GAV check
    String aliasName = name;
    String version = null;

    // Check for version syntax: alias:version
    int colonIdx = name.indexOf(':');
    if (colonIdx > 0 && !DependencyUtil.looksLikeAPossibleGav(name)) {
        aliasName = name.substring(0, colonIdx);
        version = name.substring(colonIdx + 1);

        // Validate non-empty
        if (aliasName.isEmpty() || version.isEmpty()) {
            throw new RuntimeException("Invalid alias syntax: '" + name + "'");
        }
    }

    // Existing: split on @ for catalog
    String[] parts = aliasName.split("@", 2);

    // ... existing merge logic continues with 'aliasName' instead of 'name'

    // NEW: Set requestedVersion on result
    if (a2 != null && version != null) {
        Alias result = new Alias(...);
        result.requestedVersion = version;
        return result;
    }
}
```

### Changes to `Alias.resolve()` Method

```java
public String resolve() {
    return resolve(scriptRef);
}

// Extend or override to apply version transformations
public String resolve(String scriptRef) {
    String ref = scriptRef;

    // NEW: Apply version transformations BEFORE baseRef resolution
    if (requestedVersion != null) {
        ref = applyVersion(ref, requestedVersion);
    }

    // Existing: Apply baseRef
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
```

### New `applyVersion()` Method

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
    boolean replaced = false;

    // Try each replacement type in order
    transformed = tryMavenGavReplacement(scriptRef, version, ref -> replaced = true);
    if (!replaced) transformed = tryGitHubReplacement(scriptRef, version, ref -> replaced = true);
    if (!replaced) transformed = tryGitLabReplacement(scriptRef, version, ref -> replaced = true);
    if (!replaced) transformed = tryBitbucketReplacement(scriptRef, version, ref -> replaced = true);
    if (!replaced) transformed = tryCatalogRefReplacement(scriptRef, version, ref -> replaced = true);

    if (!replaced) {
        Util.warnMsg("Version '" + version + "' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs.");
        return scriptRef;
    }

    Util.verboseMsg("Resolved script-ref: " + scriptRef + " → " + transformed);
    return transformed;
}
```

### Helper Methods for Version Replacement

```java
private String tryMavenGavReplacement(String ref, String version, Consumer<Boolean> wasReplaced) {
    if (!DependencyUtil.looksLikeAGav(ref)) {
        return ref;
    }

    Matcher m = DependencyUtil.fullGavPattern.matcher(ref);
    if (m.matches()) {
        wasReplaced.accept(true);
        String g = m.group("groupid");
        String a = m.group("artifactid");
        String c = m.group("classifier");
        String t = m.group("type");

        StringBuilder result = new StringBuilder(g).append(":").append(a).append(":").append(version);
        if (c != null) result.append(":").append(c);
        if (t != null) result.append("@").append(t);
        return result.toString();
    }

    // Try lenient pattern (no version present)
    m = DependencyUtil.lenientGavPattern.matcher(ref);
    if (m.matches() && m.group("version") == null) {
        wasReplaced.accept(true);
        return m.group("groupid") + ":" + m.group("artifactid") + ":" + version;
    }

    return ref;
}

private String tryGitHubReplacement(String ref, String version, Consumer<Boolean> wasReplaced) {
    // Pattern: https://github.com/org/repo/blob/REF/path
    String result = ref.replaceFirst(
        "(https://github\\.com/[^/]+/[^/]+/blob/)([^/]+)(/.+)",
        "$1" + version + "$3"
    );
    if (!result.equals(ref)) {
        wasReplaced.accept(true);
        return result;
    }

    // Pattern: https://raw.githubusercontent.com/org/repo/REF/path
    result = ref.replaceFirst(
        "(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/)([^/]+)(/.+)",
        "$1" + version + "$3"
    );
    if (!result.equals(ref)) {
        wasReplaced.accept(true);
    }
    return result;
}

// Similar methods for:
// - tryGitLabReplacement()
// - tryBitbucketReplacement()
// - tryCatalogRefReplacement()
```

### Pattern Sharing and Maintenance

The URL patterns for version replacement are related to those in `Util.swizzleURL()`. Both handle GitHub, GitLab, and Bitbucket URLs.

**Key differences:**
- **Swizzle**: Converts `/blob/` → `/raw/` (web UI → downloadable)
- **Version replacement**: Identifies and replaces the REF segment

**Implementation approach:**
- Extract common pattern constants if feasible (org/repo extraction)
- If patterns can't be shared due to different capture groups, add code comments linking the two locations
- When adding support for new git hosting platforms to `swizzleURL()`, consider if version replacement also needs updating

**Code comment to add:**
```java
// NOTE: These patterns are related to Util.swizzleURL() patterns.
// If git hosting platform support changes there, consider updating here.
private static final Pattern GITHUB_BLOB_PATTERN =
    Pattern.compile("^(https://github\\.com/[^/]+/[^/]+/blob/)([^/]+)(/.*)?$");
private static final Pattern GITHUB_RAW_PATTERN =
    Pattern.compile("^(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/)([^/]+)(/.*)?$");
// etc...
```

### Property Replacement Integration

Property replacement uses the existing `PropertiesValueResolver` system. The `jbang.app.version` property needs to be available when resolving `scriptRef`.

**Approach:**
1. In `applyVersion()`, create a Properties object containing `jbang.app.version` set to `requestedVersion`
2. Call `PropertiesValueResolver.replaceProperties(scriptRef, props)`
3. Compare result to original - if different, properties were active
4. If no change, apply automatic version replacement

This happens at the `Alias.resolve()` level, before the resource is fetched, which is earlier in the pipeline than the existing property replacement for dependencies/sources.

## Error Handling and User Feedback

### Verbose Logging

Always output via `Util.verboseMsg()` when version is specified:

```
"Version '1.0.0' requested for alias 'mytool'"

// Then one of:
"Applying automatic version replacement to: {original-scriptRef}"
"Property replacement active in script-ref, skipping automatic version replacement"
"No version replacement - script-ref has no recognizable version pattern"
"Catalog reference 'registered-catalog-name' doesn't support version replacement"
```

After transformation:
```
"Resolved script-ref: {original-scriptRef} → {transformed-scriptRef}"
```

### Error Cases

Throw `ExitException` with clear message:

1. **Invalid syntax**
   - Pattern: Multiple colons before `@`
   - Message: `"Invalid alias syntax: '{input}'. Expected format: alias:version@catalog"`

2. **Empty segments**
   - Pattern: `:version@catalog` or `alias:@catalog`
   - Message: `"Invalid alias syntax: empty alias name or version in '{input}'"`

3. **Property without default and no version**
   - Pattern: `${jbang.app.version}` in scriptRef, user runs without `:version`
   - Message: `"Alias requires version. Use 'alias:version@catalog' or provide default in ${jbang.app.version:default}"`

### Warning Cases

Output via `Util.warnMsg()`, then continue:

1. **Version on local alias (no catalog)**
   - User runs: `jbang mytool:1.0` (no `@`)
   - Warning: `"Version '1.0' specified but no catalog reference. Version pinning works best with catalog aliases (alias:version@catalog). Attempting to apply version to local alias."`
   - Then try to apply version replacement

2. **Version on registered catalog name**
   - scriptRef: `mytool@upstream` where `upstream` is registered
   - Warning: `"Cannot apply version to registered catalog 'upstream'. Version replacement only works with implicit catalog references (org/repo/ref)."`
   - Use original scriptRef

3. **No recognizable version pattern**
   - scriptRef: `https://example.com/download/tool.jar`
   - Warning: `"Version '1.0' specified but script-ref has no recognizable version pattern. Consider using ${jbang.app.version:default} for custom URLs."`
   - Use original scriptRef

### User-Friendly Error Messages

All error messages should:
- Show what the user provided
- Explain what's wrong
- Suggest correct syntax or workaround
- Template: `"Error: {problem}. {explanation}. Use: {correct-syntax}"`

## Testing Strategy

### Unit Tests

**1. Syntax Parsing Tests** (`TestAlias.java`)
- Valid: `mytool:1.0@catalog` → parses correctly
- Valid: `mytool:v2.0.1@catalog` → version with prefix
- Valid: `mytool:0.0.3-SNAPSHOT@org/repo` → snapshot version
- Invalid: `:1.0@catalog` → error (empty alias)
- Invalid: `mytool:@catalog` → error (empty version)
- Invalid: `mytool:v1:v2@catalog` → error (multiple colons)
- GAV passthrough: `com.example:artifact:1.0@jar` → not parsed as versioned alias

**2. Maven GAV Version Replacement Tests**
- `com.example:tool:1.0` + version `2.0` → `com.example:tool:2.0`
- `com.example:tool:1.0:classifier` + version `2.0` → `com.example:tool:2.0:classifier`
- `com.example:tool:1.0:classifier@jar` + version `2.0` → `com.example:tool:2.0:classifier@jar`
- `com.example:tool` + version `2.0` → `com.example:tool:2.0` (append)

**3. Git URL Version Replacement Tests**
- GitHub blob: `github.com/org/repo/blob/main/file.java` + `1.0` → `github.com/org/repo/blob/1.0/file.java`
- GitHub raw: `raw.githubusercontent.com/org/repo/main/file.java` + `1.0` → `raw.githubusercontent.com/org/repo/1.0/file.java`
- GitLab: `gitlab.com/org/repo/-/blob/develop/file.java` + `v1.0` → `gitlab.com/org/repo/-/blob/v1.0/file.java`
- Bitbucket: `bitbucket.org/org/repo/src/master/file.java` + `1.0` → `bitbucket.org/org/repo/src/1.0/file.java`

**4. Catalog Reference Version Replacement Tests**
- `mytool@org/catalog/dev` + version `1.0` → `mytool@org/catalog/1.0`
- `mytool@registered-name` + version `1.0` → warning, no replacement

**5. Property Replacement Tests**
- `com.example:tool:${jbang.app.version:1.0}` + version `2.0` → `com.example:tool:2.0`
- `com.example:tool:${jbang.app.version:1.0}` + no version → `com.example:tool:1.0` (default)
- `https://example.com/tool-${jbang.app.version}.jar` + version `2.0` → `https://example.com/tool-2.0.jar`
- Mixed: `com.example:tool-${jbang.app.version:1.0}:2.0` + version `3.0` → property only, `com.example:tool-3.0:2.0`

**6. Warning/Error Tests**
- File path + version → warning, no replacement
- Custom URL + version → warning, suggest property
- Local alias with version (no `@`) → warning, attempt replacement
- Invalid syntax → proper error message

### Integration Tests

**TestAliasWithVersion.java** (new file)

1. **End-to-End Alias Resolution**
   - Create catalog with Maven GAV alias
   - Run with version specification
   - Verify correct artifact resolved

2. **Alias Chaining with Version**
   - Alias A → Alias B@catalog/ref
   - Version replaces catalog ref correctly

3. **Property-based Version Control**
   - Alias with `${jbang.app.version}` in dependencies
   - Verify property flows through to dependency resolution

4. **Backward Compatibility**
   - Existing aliases without version work unchanged
   - Existing `alias@catalog` syntax unchanged

### Test Data

Extend existing test catalog files:
```json
{
  "aliases": {
    "gav-tool": {
      "script-ref": "com.example:tool:1.0.0"
    },
    "github-tool": {
      "script-ref": "https://github.com/example/repo/blob/main/tool.java"
    },
    "property-tool": {
      "script-ref": "https://example.com/tool-${jbang.app.version:1.0}.jar"
    },
    "chained-tool": {
      "script-ref": "gav-tool@upstream/develop"
    }
  }
}
```

## Backward Compatibility and Future Considerations

### Backward Compatibility Guarantees

1. **Existing Catalogs**
   - No changes required to catalog JSON files
   - All existing `script-ref` values work unchanged
   - `base-ref` behavior unchanged

2. **Existing Invocations**
   - `jbang alias@catalog` continues to work exactly as before
   - `jbang alias` (local) continues to work
   - No breaking changes to command-line syntax

3. **Existing Aliases**
   - Aliases can already have `:` in their names (rare but possible)
   - GAV detection ensures these aren't misinterpreted as versioned aliases
   - If edge cases discovered, version parsing only applies when `@catalog` present

4. **Property System**
   - `${jbang.app.version}` is a new property, no conflicts with existing ones
   - Existing property replacement logic unchanged
   - Works alongside other properties

### Non-Goals (Explicitly Out of Scope)

1. **Version Metadata in Catalogs**
   - No `versions` field in catalog JSON
   - No version discovery/listing commands
   - Users query underlying systems (Maven Central, GitHub releases)

2. **Automatic Update Notifications**
   - No "newer version available" messages
   - No automatic version checking
   - Users responsible for knowing current versions

3. **Version Constraints/Ranges**
   - No semver ranges (e.g., `>=1.0,<2.0`)
   - Exact version specification only
   - No "latest" or "RELEASE" magic keywords

4. **Lock Files**
   - No automatic recording of resolved versions
   - No `jbang.lock` file generation
   - Users specify versions explicitly each time

### Future Considerations (Potential Follow-ups)

1. **Version Discovery Command**
   - `jbang alias versions mytool@myorg`
   - Query Maven metadata or git tags
   - Display available versions

2. **Local Version Pinning**
   - `jbang alias pin mytool:1.5.0@myorg` → saves to local catalog
   - Creates local alias that locks to specific version
   - Shortcut for manual catalog editing

3. **Update Checking**
   - Opt-in flag: `jbang run --check-updates mytool:1.0@myorg`
   - Compare against latest available version
   - Advisory only, no automatic updates

4. **Additional Platforms**
   - Gitea, Gogs, self-hosted git
   - Add to version replacement patterns as needed
   - Community can contribute patterns

5. **Smart Version Prefix Handling**
   - Auto-try `v` prefix if exact version fails (git only)
   - Requires actual git operation, deferred for now

### Migration Path

For users wanting to adopt version pinning:

1. **Current state**: `jbang mytool@myorg` (runs whatever catalog has)
2. **Pinned invocation**: `jbang mytool:1.5.0@myorg` (pin to specific version)
3. **Property-based**: Update catalog to use `${jbang.app.version:1.5.0}` for complex scenarios

No migration required - purely additive feature.

## Summary

This design enables version pinning for JBang aliases through a minimal syntax extension (`alias:version@catalog`) that requires zero changes to existing catalogs. The implementation leverages existing versioning systems (Maven, git) and integrates naturally with JBang's alias resolution pipeline.

**Key Benefits:**
- Users can lock to tested versions: `jbang mytool:1.5.0@myorg`
- Fully backward compatible
- Works with Maven GAVs, git URLs, and catalog references
- Manual control via `${jbang.app.version}` property for complex cases
- Clear error messages and verbose logging for troubleshooting

**Implementation Impact:**
- Minimal changes to `Alias` class (one transient field)
- Extends existing `merge()` and `resolve()` methods
- Reuses existing `PropertiesValueResolver` infrastructure
- New helper methods for version replacement logic
- Comprehensive test coverage
