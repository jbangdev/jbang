---
updated: 2026-03-29
focus: tech
---
# Technology Stack

**Analysis Date:** 2026-03-29

## Languages

**Primary:**
- Java 8 (source/target compatibility for main production code) - All core logic in `src/main/java/`
- Java 9 (multi-release module-info) - Module descriptor in `src/main/java9/`
- Java 11 (test compilation target) - Required by WireMock; all tests in `src/test/` and `src/it/`

**Secondary:**
- Groovy - Supported as a JBang script language; runtime downloaded on demand via `src/main/java/dev/jbang/net/GroovyManager.java` (default: 4.0.30)
- Kotlin - Supported as a JBang script language; compiler downloaded on demand via `src/main/java/dev/jbang/net/KotlinManager.java`
- JShell - Supported as a JBang script format; `src/main/java/dev/jbang/source/sources/JshSource.java`
- Markdown - Supported as a JBang script format; `src/main/java/dev/jbang/source/sources/MarkdownSource.java`
- Gradle (Groovy DSL) - Build scripts `build.gradle`, `settings.gradle`

## Runtime

**Environment:**
- JVM: Java 8+ minimum (production code compiled to `--release 8`)
- Default JDK for scripts: Java 17 (`Settings.DEFAULT_JAVA_VERSION = 17`)
- Default Alpine JDK: Java 16 (`Settings.DEFAULT_ALPINE_JAVA_VERSION = 16`)
- Native Image: GraalVM support via `nativeImage` Gradle task; config in `src/native-image/config/`

**Package Manager:**
- Gradle 8.14.4 (via wrapper)
- Lockfile: `gradle/wrapper/gradle-wrapper.properties`

## Frameworks

**Core:**
- `info.picocli:picocli:4.7.7` - CLI framework for all commands; annotation-processed; entry in `src/main/java/dev/jbang/cli/`
- `io.quarkus.qute:qute-core:1.13.7.Final` - Templating engine for code generation and init templates
- `eu.maveniverse.maven.mima:context:2.4.36` + `standalone-static:2.4.36` - Maven Aether/resolver integration for dependency resolution
- `dev.jbang:devkitman:0.3.3` - JDK management (download, detect, switch); uses Foojay API internally
- `org.jline:jline-console-ui:3.30.5` + `jline-terminal-jni:3.30.5` - Interactive terminal UI (e.g., `jbang search` widget)

**Testing:**
- `org.junit:junit-bom:5.14.1` (JUnit 5 BOM) - Test runner
- `org.junit.jupiter:junit-jupiter` - JUnit Jupiter API and engine
- `org.assertj:assertj-core:3.27.7` - Fluent assertions
- `org.hamcrest:hamcrest-library:2.2` - Matcher assertions
- `org.wiremock:wiremock:3.13.2` - HTTP mock server for unit and integration tests
- `io.qameta.allure:allure-junit5:2.29.1` - Allure test reporting integration
- `org.testcontainers:testcontainers:2.0.2` - Docker-based integration tests (`src/it/java/`)
- `org.zeroturnaround:zt-exec:1.12` - Process execution helper in integration tests

**Build/Dev:**
- `com.gradleup.shadow:8.3.9` - Fat JAR / uberjar creation (produces `jbang.jar`)
- `com.diffplug.spotless:7.2.1` - Code formatting (Eclipse formatter, import ordering)
- `io.toolebox.git-versioner:1.6.7` - Automatic version from git tags (`M.m.p.c` pattern)
- `com.github.gmazzo.buildconfig:3.1.0` - Generates `dev.jbang.util.BuildConfig` with NAME and VERSION constants
- `org.gradle.crypto.checksum:1.4.0` - SHA-256 checksums for release artifacts
- `org.asciidoctor.jvm.convert:3.3.2` - AsciiDoc documentation build
- `org.ajoberstar.grgit:4.1.1` - Git introspection in build (branch, commit sha)
- `org.sonarqube:4.0.0.2929` - SonarCloud analysis
- `jacoco:0.8.14` - Code coverage (supports Java 25)
- `io.qameta.allure-report:2.12.0` - Allure HTML report generation
- `com.gradle.develocity:4.3.2` - Gradle build scans (configured in `settings.gradle`)

## Key Dependencies

**Critical:**
- `eu.maveniverse.maven.mima:context:2.4.36` - Maven Aether resolver; the entire dependency resolution pipeline depends on it; used in `src/main/java/dev/jbang/dependencies/ArtifactResolver.java`
- `dev.jbang:devkitman:0.3.3` - JDK lifecycle management; used in `src/main/java/dev/jbang/util/JavaUtil.java` and `src/main/java/dev/jbang/cli/Jdk.java`; includes `FoojayJdkInstaller` for downloading JDKs from Foojay API
- `info.picocli:picocli:4.7.7` - All CLI command parsing; `src/main/java/dev/jbang/cli/JBang.java` is the root command

**Infrastructure:**
- `org.apache.maven:maven-model:3.9.11` - POM model parsing
- `com.google.code.gson:2.13.2` - JSON parsing/serialization (catalogs, trusted sources, API responses)
- `org.jsoup:jsoup:1.17.1` - HTML parsing (for fetching scripts from web pages)
- `org.apache.commons:commons-compress:1.27.1` - Archive extraction (ZIP, TAR for downloaded JDKs, Kotlin, Groovy)
- `org.apache.commons:commons-text:1.15.0` - String utilities
- `org.jboss:jandex:2.2.3.Final` - Bytecode class index scanning
- `org.codehaus.plexus:plexus-java:1.2.0` - Java source/class analysis (module detection)
- `org.codejive:java-properties:0.0.7` - Java `.properties` file handling
- `org.jspecify:jspecify:1.0.0` - Nullability annotations (`@NonNull`, `@Nullable`)
- `org.slf4j:slf4j-nop:1.7.36` + `jcl-over-slf4j:1.7.36` - SLF4J no-op binding (suppresses logging from dependencies)
- `eu.maveniverse.maven.nisse:core:0.6.2` + `os-source:0.6.2` - OS/platform property detection for Maven

## Configuration

**Environment:**
- `JBANG_DIR` - Override JBang home directory (default: `~/.jbang`)
- `JBANG_CACHE_DIR` - Override cache directory
- `JBANG_REPO` - Override local Maven repo path
- `JBANG_LOCAL_ROOT` - Override local root directory
- `JBANG_DEFAULT_JAVA_VERSION` - Override default Java version
- `JBANG_NO_VERSION_CHECK` - Disable version update check
- `JBANG_USE_NATIVE=true` - Signal native image execution (used in CI test matrix)
- AI provider keys: `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `GEMINI_API_KEY`, `OPENCODE_API_KEY`, `GITHUB_TOKEN` - See `src/main/java/dev/jbang/ai/AIProviderFactory.java`
- `GRAALVM_HOME` - GraalVM home for native image builds

**Build:**
- `build.gradle` - Main build configuration
- `settings.gradle` - Gradle settings (Develocity, toolchain resolver)
- `misc/eclipse_formatting_nowrap.xml` - Spotless Java formatting rules
- `src/native-image/config/` - GraalVM reflection/resource config for native builds

## Platform Requirements

**Development:**
- Java 11+ toolchain (Adoptium Temurin) for compilation (`java { toolchain { languageVersion = 11 } }`)
- Gradle 8.14.4 (via wrapper `./gradlew`)
- Docker (optional, for integration tests using Testcontainers)
- GraalVM (optional, for `./gradlew nativeImage`)

**Production:**
- Distributed as uberjar (`jbang.jar`) plus shell wrapper scripts (`bin/jbang`, `bin/jbang.cmd`, `bin/jbang.ps1`)
- Packaged as `.zip` and `.tar` distributions
- Docker images published to: `docker.io/jbangdev/jbang`, `quay.io/jbangdev/jbang`, `ghcr.io/jbangdev/jbang`
- Java 8+ at runtime minimum; scripts default to Java 17

---

*Stack analysis: 2026-03-29*
