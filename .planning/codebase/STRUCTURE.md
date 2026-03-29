---
updated: 2026-03-29
focus: arch
---
# Codebase Structure

**Analysis Date:** 2026-03-29

## Directory Layout

```
jbang/
├── src/
│   ├── main/
│   │   ├── assembly/           # Distribution assembly descriptors
│   │   └── java/dev/jbang/
│   │       ├── Main.java       # JVM entry point
│   │       ├── Configuration.java  # Layered config system
│   │       ├── Settings.java   # Path constants and env var names
│   │       ├── Cache.java      # Cache class enum
│   │       ├── ai/             # AI provider abstraction
│   │       ├── catalog/        # Alias/template/catalog management
│   │       ├── cli/            # All picocli commands and mixins
│   │       ├── dependencies/   # Maven dependency resolution
│   │       ├── net/            # JDK/editor/trust management
│   │       ├── resources/      # ResourceRef and resolver chain
│   │       │   └── resolvers/  # Concrete ResourceResolver implementations
│   │       ├── search/         # Artifact search (Maven Central)
│   │       ├── source/         # Core build pipeline
│   │       │   ├── buildsteps/ # Compile, jar, integration, native steps
│   │       │   ├── generators/ # Command-line generators (jar, jsh, native)
│   │       │   ├── parser/     # In-source directive parser (//DEPS etc.)
│   │       │   ├── sources/    # Concrete Source types (Java, Kotlin, etc.)
│   │       │   └── update/     # Source file update strategies
│   │       ├── spi/            # Build-time integration SPI
│   │       └── util/           # General utilities
│   ├── test/
│   │   ├── java/dev/jbang/     # Unit tests (mirrors main package structure)
│   │   └── resources/          # Test fixtures, WireMock mappings
│   ├── it/
│   │   ├── java/dev/jbang/it/  # Integration test source
│   │   └── resources/          # Integration test resources
│   └── jreleaser/              # JReleaser release automation templates
├── itests/                     # Shell-based integration test scripts and fixtures
├── docs/                       # Antora documentation source
├── examples/                   # Example jbang scripts
├── misc/                       # Demos and proxy configuration
├── images/                     # Docker/container image definitions
├── gradle/wrapper/             # Gradle wrapper
├── build.gradle                # Main build script
├── settings.gradle             # Gradle settings (project name)
├── gradlew / gradlew.bat       # Gradle wrapper scripts
├── jreleaser.yml               # JReleaser release config
└── pipelines.puml              # Architecture diagram (PlantUML)
```

## Directory Purposes

**`src/main/java/dev/jbang/cli/`:**
- Purpose: All user-facing CLI commands and shared option groups (mixins)
- Contains: `JBang.java` (root), `BaseCommand.java`, `BaseBuildCommand.java`, one class per sub-command (`Run.java`, `Build.java`, `Edit.java`, `Init.java`, `Alias.java`, `Template.java`, `Catalog.java`, `Trust.java`, `Cache.java`, `Jdk.java`, `App.java`, `Export.java`, `Config.java`, `Deps.java`, `Info.java`, `Version.java`, `Wrapper.java`, `Completion.java`), mixin classes (`RunMixin.java`, `BuildMixin.java`, `ScriptMixin.java`, `NativeMixin.java`, `DependencyInfoMixin.java`, `ExportMixin.java`, `FormatMixin.java`, `JdkProvidersMixin.java`), `ExitException.java`
- Key files: `src/main/java/dev/jbang/cli/JBang.java`, `src/main/java/dev/jbang/cli/Run.java`, `src/main/java/dev/jbang/cli/BaseCommand.java`

**`src/main/java/dev/jbang/source/`:**
- Purpose: The core build pipeline
- Key files: `src/main/java/dev/jbang/source/Project.java`, `src/main/java/dev/jbang/source/ProjectBuilder.java`, `src/main/java/dev/jbang/source/AppBuilder.java`, `src/main/java/dev/jbang/source/BuildContext.java`, `src/main/java/dev/jbang/source/Source.java`, `src/main/java/dev/jbang/source/Builder.java`

**`src/main/java/dev/jbang/source/sources/`:**
- Purpose: One class per supported source language/format
- Contains: `JavaSource.java`, `KotlinSource.java`, `GroovySource.java`, `JshSource.java`, `MarkdownSource.java`

**`src/main/java/dev/jbang/source/buildsteps/`:**
- Purpose: Individual pipeline steps that transform a `Project`
- Contains: `CompileBuildStep.java` (invokes `javac`/`kotlinc`/`groovyc`), `JarBuildStep.java` (packages compiled classes), `IntegrationBuildStep.java` (runs SPI integrations), `NativeBuildStep.java` (GraalVM native-image)

**`src/main/java/dev/jbang/source/generators/`:**
- Purpose: Generate the final `java` (or native binary) command line string
- Contains: `BaseCmdGenerator.java`, `JarCmdGenerator.java`, `JshCmdGenerator.java`, `NativeCmdGenerator.java`

**`src/main/java/dev/jbang/source/parser/`:**
- Purpose: Parse `//`-directive comments embedded in source files
- Key file: `src/main/java/dev/jbang/source/parser/Directives.java`

**`src/main/java/dev/jbang/catalog/`:**
- Purpose: Read/write `jbang-catalog.json` files; resolve aliases and templates
- Key files: `src/main/java/dev/jbang/catalog/Catalog.java`, `src/main/java/dev/jbang/catalog/Alias.java`, `src/main/java/dev/jbang/catalog/Template.java`

**`src/main/java/dev/jbang/dependencies/`:**
- Purpose: All Maven dependency resolution logic
- Key files: `src/main/java/dev/jbang/dependencies/DependencyUtil.java`, `src/main/java/dev/jbang/dependencies/DependencyResolver.java`, `src/main/java/dev/jbang/dependencies/ArtifactResolver.java`, `src/main/java/dev/jbang/dependencies/MavenCoordinate.java`

**`src/main/java/dev/jbang/resources/`:**
- Purpose: Abstractions for locating resources (files, URLs, Maven artifacts)
- Key files: `src/main/java/dev/jbang/resources/ResourceResolver.java` (interface), `src/main/java/dev/jbang/resources/ResourceRef.java`, `src/main/java/dev/jbang/resources/resolvers/CombinedResourceResolver.java`

**`src/main/java/dev/jbang/net/`:**
- Purpose: JDK download and management, editor integration (VS Code, etc.), Kotlin/Groovy runtime management
- Key files: `src/main/java/dev/jbang/net/EditorManager.java`, `src/main/java/dev/jbang/net/KotlinManager.java`, `src/main/java/dev/jbang/net/GroovyManager.java`, `src/main/java/dev/jbang/net/TrustedSources.java`

**`src/main/java/dev/jbang/util/`:**
- Purpose: Shared utilities (OS detection, file I/O, process execution, template rendering, version checking)
- Key files: `src/main/java/dev/jbang/util/Util.java` (central utility class), `src/main/java/dev/jbang/util/JavaUtil.java`, `src/main/java/dev/jbang/util/CommandBuffer.java`, `src/main/java/dev/jbang/util/TemplateEngine.java`

**`src/main/java/dev/jbang/spi/`:**
- Purpose: Service Provider Interface for third-party build-time integrations
- Contains: `src/main/java/dev/jbang/spi/IntegrationManager.java`, `src/main/java/dev/jbang/spi/IntegrationInput.java`, `src/main/java/dev/jbang/spi/IntegrationResult.java`

**`src/test/java/dev/jbang/`:**
- Purpose: JUnit 5 unit tests, mirroring the main package structure
- Contains test classes under `cli/`, `dependencies/`, `net/`, `source/`, `source/parser/`, `util/`, `search/`
- Key files: `src/test/java/dev/jbang/BaseTest.java` (shared test base class)

**`src/it/java/dev/jbang/it/`:**
- Purpose: Integration tests that invoke the full JBang binary
- Generated: No; Committed: Yes

**`itests/`:**
- Purpose: Shell-script-based integration tests with fixture scripts
- Generated: No; Committed: Yes

**`src/jreleaser/`:**
- Purpose: JReleaser release automation — Docker images, Homebrew, Snap, GitHub Actions distribution templates
- Generated: No; Committed: Yes

## Key File Locations

**Entry Points:**
- `src/main/java/dev/jbang/Main.java`: JVM main class; bootstraps picocli and handles implicit `run` defaulting
- `src/main/java/dev/jbang/cli/JBang.java`: Root picocli command; configures all handlers and sub-commands

**Configuration:**
- `src/main/java/dev/jbang/Configuration.java`: Layered config with fallback chain
- `src/main/java/dev/jbang/Settings.java`: Env var names, default values, path helpers
- `build.gradle`: Gradle build configuration, all dependency versions
- `settings.gradle`: Project name

**Core Logic:**
- `src/main/java/dev/jbang/source/ProjectBuilder.java`: Orchestrates source resolution into a `Project`
- `src/main/java/dev/jbang/source/AppBuilder.java`: Orchestrates compilation and packaging pipeline
- `src/main/java/dev/jbang/source/parser/Directives.java`: In-source directive parser
- `src/main/java/dev/jbang/dependencies/DependencyUtil.java`: Maven GAV parsing and repo aliases

**Testing:**
- `src/test/java/dev/jbang/BaseTest.java`: Base class for unit tests
- `src/test/resources/wiremock/`: WireMock stubs for HTTP mocking in tests

## Naming Conventions

**Files:**
- All production Java files: `PascalCase.java` — e.g., `ProjectBuilder.java`, `JarCmdGenerator.java`
- Test files: `Test` prefix — e.g., `TestBuilder.java`, `TestDirectives.java`; or `Test` suffix for utilities
- CLI mixin classes: `*Mixin.java` suffix — e.g., `RunMixin.java`, `BuildMixin.java`
- CLI command classes: Descriptive noun, no suffix — e.g., `Run.java`, `Build.java`, `Catalog.java`
- Build step classes: `*BuildStep.java` suffix
- Command generator classes: `*CmdGenerator.java` suffix
- Resource resolver classes: `*ResourceResolver.java` suffix

**Directories:**
- All lowercase, plural where appropriate: `sources/`, `buildsteps/`, `generators/`, `resolvers/`
- Package names match directory: `dev.jbang.source.sources`, `dev.jbang.source.buildsteps`

## Where to Add New Code

**New CLI sub-command:**
- Implementation: `src/main/java/dev/jbang/cli/MyCommand.java` extending `BaseCommand` or `BaseBuildCommand`
- Register: Add to the `subcommands = { ... }` list in `@Command` annotation on `src/main/java/dev/jbang/cli/JBang.java`
- Tests: `src/test/java/dev/jbang/cli/TestMyCommand.java`

**New source language type:**
- Implementation: `src/main/java/dev/jbang/source/sources/MyLangSource.java` extending `Source`
- Add nested `MyLangAppBuilder` extending `AppBuilder`
- Add `Type` enum value in `src/main/java/dev/jbang/source/Source.java`

**New resource resolver:**
- Implementation: `src/main/java/dev/jbang/resources/resolvers/MyResourceResolver.java` implementing `ResourceResolver`
- Register: Add to `CombinedResourceResolver` chain in `src/main/java/dev/jbang/source/ProjectBuilder.java`

**New directive (in-source comment):**
- Add constant to `Directives.Names` inner class in `src/main/java/dev/jbang/source/parser/Directives.java`
- Add parsing method on `Directives` abstract class
- Implement parsing logic in `Directives.Extended` inner class

**New build step:**
- Implementation: `src/main/java/dev/jbang/source/buildsteps/MyBuildStep.java` implementing `Builder<Project>`
- Invoke it in the appropriate `AppBuilder.build()` method

**Shared utilities:**
- General helpers: `src/main/java/dev/jbang/util/Util.java` (for small additions) or new file in `src/main/java/dev/jbang/util/`
- Configuration constants: `src/main/java/dev/jbang/Settings.java`
