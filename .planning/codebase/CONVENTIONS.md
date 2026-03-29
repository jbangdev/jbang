---
updated: 2026-03-29
focus: quality
---
# Coding Conventions

**Analysis Date:** 2026-03-29

## Naming Patterns

**Files:**
- PascalCase for all Java source files: `ProjectBuilder.java`, `BaseCommand.java`, `NetUtil.java`
- Test files prefixed with `Test`: `TestSource.java`, `TestProjectBuilder.java`, `TestArguments.java`
- Integration test files suffixed with `IT`: `RunIT.java`, `AliasIT.java`, `DependenciesIT.java`
- CLI mixin classes suffixed with `Mixin`: `RunMixin.java`, `BuildMixin.java`, `ScriptMixin.java`
- Utility classes suffixed with `Util`: `Util.java`, `NetUtil.java`, `JavaUtil.java`, `DependencyUtil.java`

**Classes:**
- PascalCase for all types: `ProjectBuilder`, `BuildContext`, `ExitException`
- Abstract base classes prefixed with `Base`: `BaseCommand`, `BaseBuildCommand`, `BaseTest`, `BaseIT`
- Manager classes suffixed with `Manager`: `JdkManager`, `IntegrationManager`, `EditorManager`
- Abstract assertion helpers extend `AbstractAssert` and follow the `XxxAssert` pattern: `CommandResultAssert`

**Methods:**
- camelCase for all methods: `getResourceRef()`, `addRepository()`, `setJavaVersion()`
- Boolean getters use `is` prefix: `isVerbose()`, `isWindows()`, `isOffline()`
- Builders use fluent method chaining returning `this`/the builder type: `Project.builder()`, `pb.build(src)`

**Variables and Fields:**
- camelCase: `jbangTempDir`, `cwdDir`, `testJavaMajorVersion`
- Constants: `SCREAMING_SNAKE_CASE` — `JBANG_DIR`, `EXIT_OK`, `EXIT_GENERIC_ERROR`, `EXAMPLES_FOLDER`
- Static final fields use `SCREAMING_SNAKE_CASE`: `EXIT_OK = 0`, `EXIT_INVALID_INPUT = 2`

**Packages:**
- All lowercase: `dev.jbang.cli`, `dev.jbang.source`, `dev.jbang.util`, `dev.jbang.dependencies`

## Code Style

**Formatting:**
- Tool: Spotless with Eclipse formatter config (`misc/eclipse_formatting_nowrap.xml`)
- Line endings: UNIX (`\n`)
- Indentation: tabs (4-space equivalent, enforced by `leadingSpacesToTabs(4)`)
- Trailing whitespace trimmed
- Files must end with newline

**Linting:**
- Spotless enforces import order and removes unused imports
- `javac -Xlint:deprecation` is applied to all compiled code
- `-parameters` compiler flag is set to support Allure reporting reflection

## Import Organization

**Order (enforced by Spotless):**
1. `java.*`
2. `javax.*`
3. `org.*`
4. `com.*`
5. `dev.jbang.*`
6. Blank line, then everything else (third-party not in above groups, e.g., `picocli`)

**Static imports:**
- Grouped together at the top of the import block, before non-static imports — test files place `static org.hamcrest.*` and `static org.junit.*` imports first

**No path aliases** — standard Java package imports only.

## Error Handling

**Patterns:**
- User-facing errors are thrown as `ExitException` (extends `RuntimeException`) from `dev.jbang.cli.ExitException`
- Exit codes are constants on `BaseCommand`: `EXIT_OK=0`, `EXIT_GENERIC_ERROR=1`, `EXIT_INVALID_INPUT=2`, `EXIT_UNEXPECTED_STATE=3`, `EXIT_INTERNAL_ERROR=4`, `EXIT_EXECUTE=255`
- Checked exceptions are generally propagated (`throws IOException`) or wrapped in `ExitException`
- Internal errors that should not surface to users: wrapped in `RuntimeException` or `IllegalStateException`

**Pattern:**
```java
throw new ExitException(EXIT_INVALID_INPUT, "No alias found with name '" + aliasName + "'");
throw new ExitException(EXIT_GENERIC_ERROR, "Issue running postBuild()", cause);
```

## Logging

**Framework:** `java.util.logging` (JUL) — no SLF4J/Log4j in application code (SLF4J NOP binding silences Maven/library noise)

**Patterns via `dev.jbang.util.Util` static helpers:**
- `Util.verboseMsg(String)` — debug-level output, shown when `--verbose` is active
- `Util.verboseMsg(String, Throwable)` — verbose with stack trace
- `Util.infoMsg(String)` — informational, shown unless `--quiet`
- `Util.warnMsg(String)` — warnings to stderr
- `Util.errorMsg(String)` — errors to stderr
- `Util.errorMsg(String, Throwable)` — errors with optional stack trace

Do NOT use `System.out.println` for application messages. Use the `Util.*Msg` helpers.

## Comments

**When to Comment:**
- Use `//` inline comments to explain non-obvious intent (e.g., `// HACK it's a crappy way to work around...`)
- Use `TODO:` prefix for known incomplete or deferred work
- Use `FIXME:` / `HACK:` for known workarounds
- Javadoc (`/** */`) is used on public classes and key methods, especially in `source` and `util` packages

**Javadoc:**
- Applied selectively — key domain classes like `Project` have class-level Javadoc
- `options.addStringOption('Xdoclint:none', '-quiet')` suppresses missing-tag warnings, so partial Javadoc is accepted

## Function Design

**Size:** Methods tend to be small and focused. Builders use step-by-step configuration methods.

**Parameters:** Prefer passing domain objects over primitives; `@NonNull` and `@Nullable` annotations (from `org.jspecify`) used on public API parameters and returns

**Return Values:**
- Fluent builder methods return the builder instance (`Project`, `ProjectBuilder`)
- Optional results returned as `java.util.Optional<T>` rather than null
- Commands return `Integer` (exit code) implementing `Callable<Integer>`

## Module Design

**Exports:**
- Classes are package-scoped by default; only those intended for cross-package use are `public`
- No Java 9 module-info for the main artifact (compiled with `--release 8`); a `src/main/java9` source set adds multi-release jar support for Java 9 module descriptor

**Null Safety:**
- `org.jspecify` annotations (`@NonNull`, `@Nullable`) used in domain-layer code (`source`, `util`)
- Unannotated code should be treated as potentially nullable

**CLI structure:**
- Each subcommand is its own `public` class in `dev.jbang.cli`, annotated with `@CommandLine.Command`
- Shared options are extracted into Mixin classes (`RunMixin`, `BuildMixin`, `ScriptMixin`, etc.)
- All commands extend `BaseCommand` which implements `Callable<Integer>`
