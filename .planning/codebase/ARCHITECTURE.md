---
updated: 2026-03-29
focus: arch
---
# Architecture

**Analysis Date:** 2026-03-29

## Pattern Overview

**Overall:** Layered CLI tool with a pipeline-based build model

JBang is a Java-based CLI tool that resolves, compiles, and executes Java/JVM source files on demand. The architecture follows a strict pipeline: **CLI command** → **resource resolution** → **project construction** → **build steps** → **command generation** → **execution hand-off**.

**Key Characteristics:**
- CLI entry point delegates to sub-commands via picocli; all commands extend `BaseCommand`
- Source execution is a multi-step pipeline: resolve → build → generate run command → `System.exit(255)` to signal the shell wrapper to exec
- The JVM process does not directly exec the compiled code; it prints a command line and exits with code 255, and the shell wrapper (`jbang` bash/bat script) execs that command
- Heavy use of the Builder pattern throughout: `ProjectBuilder`, `AppBuilder`, `CmdGeneratorBuilder`, `CmdGenerator`
- Resource resolution uses a Chain-of-Responsibility pattern via `CombinedResourceResolver`

## Layers

**CLI Layer:**
- Purpose: Parse user arguments, configure global state, dispatch to command logic
- Location: `src/main/java/dev/jbang/cli/`
- Contains: `JBang.java` (root command), `BaseCommand.java`, `BaseBuildCommand.java`, all concrete command classes (`Run.java`, `Build.java`, `Edit.java`, etc.), and `*Mixin` classes for shared option groups
- Depends on: `source`, `catalog`, `resources`, `util`, `Configuration`, `Settings`
- Used by: `Main.java` (entry point)

**Source / Build Pipeline Layer:**
- Purpose: Represent source files as `Project` objects and transform them to runnable artifacts
- Location: `src/main/java/dev/jbang/source/`
- Contains: `Project.java`, `ProjectBuilder.java`, `AppBuilder.java` (abstract), `BuildContext.java`, `Source.java` (abstract), `SourceSet.java`, `CodeBuilderProvider.java`, `CmdGenerator.java`, `CmdGeneratorBuilder.java`, `Builder.java` interface
- Sub-packages:
  - `sources/`: Concrete source types — `JavaSource.java`, `KotlinSource.java`, `GroovySource.java`, `JshSource.java`, `MarkdownSource.java`
  - `buildsteps/`: `CompileBuildStep.java`, `JarBuildStep.java`, `IntegrationBuildStep.java`, `NativeBuildStep.java`
  - `generators/`: `JarCmdGenerator.java`, `JshCmdGenerator.java`, `NativeCmdGenerator.java`, `BaseCmdGenerator.java`
  - `parser/`: `Directives.java`, `KeyValue.java` — parse `//DEPS`, `//SOURCES`, `//JAVA` and other in-source directives
  - `update/`: Source file update strategies for `jbang edit` and dependency updates
- Depends on: `resources`, `dependencies`, `catalog`, `spi`, `util`, `Settings`, `Configuration`
- Used by: CLI layer commands

**Resource Resolution Layer:**
- Purpose: Map a string reference (file path, URL, GAV, alias, literal code) to a concrete `ResourceRef`
- Location: `src/main/java/dev/jbang/resources/`, `src/main/java/dev/jbang/resources/resolvers/`
- Contains: `ResourceResolver.java` (interface), `ResourceRef.java`, `ResourceNotFoundException.java`, `InputStreamResourceRef.java`
- Resolvers: `FileResourceResolver`, `RemoteResourceResolver`, `GavResourceResolver`, `AliasResourceResolver`, `LiteralScriptResourceResolver`, `ClasspathResourceResolver`, `RenamingScriptResourceResolver`, `TrustingResourceResolver`, `CombinedResourceResolver`
- Depends on: `catalog`, `dependencies`, `net`, `util`
- Used by: `ProjectBuilder.java`

**Dependency Resolution Layer:**
- Purpose: Resolve Maven coordinates to local JAR paths via Maven Resolver (MIMA)
- Location: `src/main/java/dev/jbang/dependencies/`
- Contains: `DependencyResolver.java`, `DependencyUtil.java`, `ArtifactResolver.java`, `ArtifactInfo.java`, `DependencyCache.java`, `MavenCoordinate.java`, `MavenRepo.java`, `ModularClassPath.java`, `Detector.java` (OS/arch), `JitPackUtil.java`
- Depends on: `eu.maveniverse.maven.mima` (MIMA Maven Resolver facade), `util`, `Settings`
- Used by: `ProjectBuilder.java`, `source` layer

**Catalog Layer:**
- Purpose: Manage named aliases, templates, and catalog references (local and remote `jbang-catalog.json` files)
- Location: `src/main/java/dev/jbang/catalog/`
- Contains: `Catalog.java`, `Alias.java`, `CatalogItem.java`, `CatalogRef.java`, `CatalogUtil.java`, `Template.java`, `TemplateProperty.java`, `ImplicitCatalogRef.java`
- Depends on: `resources`, `util`, `Settings`, Gson (JSON)
- Used by: CLI layer, `ProjectBuilder.java`, `Main.java`

**Network Layer:**
- Purpose: JDK download/management, trust management, HTTP utilities
- Location: `src/main/java/dev/jbang/net/`
- Contains: `EditorManager.java`, `GroovyManager.java`, `KotlinManager.java`, `TrustedSources.java`
- Depends on: `devkitman` (JDK management external library), `util`
- Used by: CLI commands (`Jdk.java`, `Trust.java`), `ProjectBuilder.java`

**SPI Layer:**
- Purpose: Build-time integration point allowing third-party tools to hook into JBang's build pipeline
- Location: `src/main/java/dev/jbang/spi/`
- Contains: `IntegrationManager.java`, `IntegrationInput.java`, `IntegrationResult.java`
- Depends on: `source`, `util`, Gson
- Used by: `IntegrationBuildStep.java`

**AI Layer:**
- Purpose: AI-assisted dependency and code suggestions
- Location: `src/main/java/dev/jbang/ai/`
- Contains: `AIProvider.java` (interface), `AIProviderFactory.java`, `OpenAIProvider.java`, `ChatResponse.java`
- Used by: CLI AI options (`AIOptions.java`)

**Search Layer:**
- Purpose: Artifact search via Maven Central / Solr
- Location: `src/main/java/dev/jbang/search/`
- Contains: `ArtifactSearch.java`, `SolrArtifactSearch.java`, `ArtifactSearchWidget.java`, `Fuzz.java`, `SearchScorer.java`, `SearchUtil.java`, `ComboBox.java`
- Used by: CLI `Deps.java` command

**Configuration / Settings:**
- Purpose: Layered key-value configuration (defaults → global → local → flags), global path constants
- Location: `src/main/java/dev/jbang/Configuration.java`, `src/main/java/dev/jbang/Settings.java`
- `Configuration.java`: Hierarchical config with fallback chain; reads `jbang.properties` files; provides default values to picocli via `ConfigurationResourceBundle`
- `Settings.java`: Static constants and path helpers (`~/.jbang/`, `JBANG_DIR`, `JBANG_CACHE_DIR`, etc.)

## Data Flow

**Primary Run Flow (`jbang MyScript.java`):**

1. `Main.main()` calls `handleDefaultRun()` to prepend implicit `run` sub-command to args
2. picocli routes to `Run.doCall()`
3. `BaseBuildCommand.createBaseProjectBuilder()` assembles a `ProjectBuilder` with all CLI options
4. `ProjectBuilder.build(scriptOrFile)` resolves the resource via a chain of `ResourceResolver`s to get a `ResourceRef`
5. `ProjectBuilder` parses in-source `//DEPS`, `//SOURCES`, `//JAVA` directives via `Directives`
6. `ProjectBuilder` resolves Maven dependencies via `DependencyResolver`, producing a `ModularClassPath`
7. A `Project` object is constructed with all source sets, dependencies, and metadata
8. `BuildContext.forProject(project)` establishes the build directory (under `~/.jbang/cache/jars/`)
9. `Project.codeBuilder(ctx).build()` dispatches to the appropriate `AppBuilder` subclass (e.g., `JavaSource.JavaAppBuilder`)
10. `AppBuilder.build()` runs build steps in order: `CompileBuildStep` → `IntegrationBuildStep` → `JarBuildStep` → optionally `NativeBuildStep`
11. A `CmdGeneratorBuilder` is returned; `Run.updateGeneratorForRun()` sets runtime options, arguments
12. `CmdGeneratorBuilder.build()` creates a `JarCmdGenerator` (or `JshCmdGenerator` / `NativeCmdGenerator`)
13. `CmdGenerator.generate()` produces a full shell command string
14. The command string is printed to stdout; `Run.doCall()` returns `EXIT_EXECUTE` (255)
15. The outer `jbang` shell script detects exit code 255 and `exec`s the printed command

**Artifact Resolution:**
1. Input string examined by each `ResourceResolver` in chain (alias → file → remote → GAV → literal)
2. First resolver that recognizes the format returns a `ResourceRef`
3. Remote/GAV resources are downloaded/fetched to the local cache

**State Management:**
- Global mutable state held in `Util` static fields (verbose, quiet, offline, fresh, preview flags); set during CLI option parsing via picocli `@Option` setter methods
- `Configuration` uses a thread-local-ish singleton pattern (`Configuration.instance()`) with a fallback chain
- Build cache keyed by source file name + stable hash (`project.getStableId()`) under `~/.jbang/cache/jars/`

## Key Abstractions

**`Source` (abstract):**
- Purpose: Represents a single source file; parses directives lazily; knows its type
- Examples: `src/main/java/dev/jbang/source/sources/JavaSource.java`, `KotlinSource.java`, `GroovySource.java`, `JshSource.java`, `MarkdownSource.java`
- Pattern: Template Method — subclasses override `getType()`, `getCompileOptions()`, `getBuilder()`

**`Project`:**
- Purpose: Aggregates everything needed to build: sources, dependencies, repos, JVM options, metadata
- File: `src/main/java/dev/jbang/source/Project.java`
- Pattern: Value object / data bag; created exclusively via `ProjectBuilder`

**`ProjectBuilder`:**
- Purpose: Fluent builder that resolves a resource reference into a fully-populated `Project`
- File: `src/main/java/dev/jbang/source/ProjectBuilder.java`
- Pattern: Builder; heavy orchestration logic lives here

**`AppBuilder` (abstract):**
- Purpose: Orchestrates build steps for a `Project` to produce a runnable artifact
- File: `src/main/java/dev/jbang/source/AppBuilder.java`
- Pattern: Template Method; concrete builders per source type nested inside `JavaSource`, `KotlinSource`, etc.

**`Builder<T>` (interface):**
- Purpose: Single-method functional interface `T build() throws IOException` used uniformly for all pipeline stages
- File: `src/main/java/dev/jbang/source/Builder.java`

**`ResourceResolver` (interface):**
- Purpose: Chain-of-Responsibility link for resolving resource strings to `ResourceRef`
- File: `src/main/java/dev/jbang/resources/ResourceResolver.java`
- Pattern: Chain of Responsibility via `CombinedResourceResolver`

**`Directives`:**
- Purpose: Parses `//`-prefixed in-source comments (`//DEPS`, `//SOURCES`, `//JAVA`, etc.) into structured data
- File: `src/main/java/dev/jbang/source/parser/Directives.java`
- Pattern: Abstract class with `Basic` and `Extended` inner subclasses

**`BuildContext`:**
- Purpose: Holds a `Project` and its associated build directory; provides derived paths (jar file, native image file)
- File: `src/main/java/dev/jbang/source/BuildContext.java`
- Pattern: Context object threading state through the build pipeline

## Entry Points

**`Main.main()`:**
- Location: `src/main/java/dev/jbang/Main.java`
- Triggers: JVM launch via `java -jar jbang.jar` or via the `jbang` shell wrapper
- Responsibilities: Initialize picocli `CommandLine`, handle implicit `run` defaulting, dispatch

**`JBang.getCommandLine()`:**
- Location: `src/main/java/dev/jbang/cli/JBang.java`
- Responsibilities: Configure picocli with exception handlers, execution strategy, default value provider (reads from `Configuration`), resource bundle

## Error Handling

**Strategy:** Exit codes + `ExitException`

**Patterns:**
- `ExitException` (extends `RuntimeException`) carries an integer exit code; caught by `JBang.executionExceptionHandler` to translate to process exit
- Exit codes defined as constants on `BaseCommand`: `EXIT_OK=0`, `EXIT_GENERIC_ERROR=1`, `EXIT_INVALID_INPUT=2`, `EXIT_UNEXPECTED_STATE=3`, `EXIT_INTERNAL_ERROR=4`, `EXIT_EXECUTE=255`
- `ResourceNotFoundException` for missing resources
- `DependencyException` for Maven resolution failures
- All user-facing error messages go to stderr via `Util.errorMsg()` / `Util.warnMsg()`

## Cross-Cutting Concerns

**Logging:** `java.util.logging` (JUL); configured via `src/main/resources/logging.properties`; user-facing output through `Util.verboseMsg()`, `Util.infoMsg()`, `Util.warnMsg()`, `Util.errorMsg()` which respect quiet/verbose flags

**Validation:** Primarily done inside CLI command `doCall()` methods and `ProjectBuilder`; no cross-cutting validation framework

**Authentication:** Trust model for remote scripts managed by `TrustedSources` and enforced in `TrustingResourceResolver`; SSL can be disabled globally via `--insecure` (disables all cert checking in `BaseCommand.enableInsecure()`)
