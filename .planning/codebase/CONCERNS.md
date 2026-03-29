---
updated: 2026-03-29
focus: concerns
---
# CONCERNS

## Tech Debt

1. **`Util.java` god class** — Massive utility class with too many unrelated responsibilities; needs decomposition.
2. **Global mutable static state** — Several singletons and static fields make testing and concurrency difficult.
3. **Non-thread-safe catalog cache** — The artifact catalog cache is not thread-safe, risking corruption under concurrent access.
4. **Deprecated `Project` constructor** — Old constructor still referenced internally; should be removed.
5. **Misplaced `runtimeOptions`** — Runtime options logic lives in the wrong layer/class.
6. **Duplication between `App.java` / `Wrapper.java`** — Significant logic overlap between these two entry points.
7. **Lingering deprecated CLI flag** — At least one deprecated CLI flag still present without removal timeline.

## Known Bugs / Fragile Behavior

1. **Interactive-mode HACK in `Run.java`** — Comment-marked hack for interactive mode that needs a proper fix.
2. **`File.renameTo()` instead of `Files.move()`** — Legacy file rename that silently fails across filesystems.
3. **Edit-live always rebuilds** — The edit-live feature triggers a full rebuild every time regardless of changes.
4. **Incomplete debug special-character handling** — Debug mode doesn't properly escape all special characters in shell arguments.

## Security Considerations

1. **URL swizzler applied unconditionally on redirects** — Redirect-following logic applies URL rewriting without sufficient validation.
2. **Hard-coded trust bypass for `github.com/jbangdev/`** — Implicit trust for jbangdev GitHub URLs bypasses normal trust checks.
3. **API keys exposed in verbose log output** — Verbose/debug logging can leak API keys or credentials to stdout.
4. **Credential property-expansion interaction** — Property expansion in script headers can interact unexpectedly with credential handling.

## Performance Concerns

1. **Blocking terminal I/O in search widget** — `ArtifactSearchWidget` performs blocking I/O on the main thread.
2. **Always-full rebuild in edit-live** — No incremental compilation; every save triggers a complete rebuild.
3. **Unbounded catalog cache** — The in-memory catalog cache has no eviction policy and grows without bound.

## Fragile Areas

1. **`System.setOut` mutation in `IntegrationManager`** — Replaces the global stdout stream, which is process-wide and non-reversible.
2. **Incomplete PowerShell safe-char set** — Shell escaping for PowerShell may miss edge-case characters.
3. **Native-image JAR path fallback without helpful error** — When the native image can't find its JAR, the error message is unhelpful.
4. **Brittle og:description scraping** — Metadata scraping relies on HTML structure that can change without notice.

## Missing Tests

1. **AI package has zero unit tests** — The entire AI/LLM integration package lacks unit test coverage.
2. **`IntegrationManager` external path** — The external integration execution path is untested.
3. **`NetUtil`** — Network utility methods have no unit tests.
4. **`ArtifactSearchWidget`** — The search widget has no automated tests.

## Deprecated Patterns

1. **`mavencentral` alias** — Old alias still supported; should be removed in a future major version.
2. **Commented-out AI providers** — Dead code for AI provider integrations left as comments rather than removed.

## Miscellaneous

1. **Direct `URL.openStream()` in `EditorManager`** — Bypasses the centralized HTTP client with redirect handling and timeout config.
2. **Swing dependency in a CLI tool** — Some UI components pull in Swing, which is inappropriate for a CLI-first tool.
3. **`outFile` path handling TODO** — There is a known TODO around output file path resolution that has not been addressed.
