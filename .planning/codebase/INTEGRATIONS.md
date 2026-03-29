---
updated: 2026-03-29
focus: tech
---
# External Integrations

**Analysis Date:** 2026-03-29

## APIs & External Services

**Maven Repositories (dependency resolution):**
- Maven Central - `https://repo1.maven.org/maven2/` - primary artifact resolution
- Sonatype Central Portal - `https://central.sonatype.com/repository/maven-snapshots/` - snapshots
- JitPack - `https://jitpack.io/` - GitHub/GitLab/Bitbucket source builds via `src/main/java/dev/jbang/dependencies/JitPackUtil.java`
- JBoss, RedHat, Google, Spring, JCenter, Sponge, mvnpm - pre-registered repo aliases in `src/main/java/dev/jbang/dependencies/DependencyUtil.java`
- SDK/Client: Eclipse Aether via `eu.maveniverse.maven.mima` in `src/main/java/dev/jbang/dependencies/ArtifactResolver.java`
- Auth: `JBANG_AUTH_BASIC_USERNAME` / `JBANG_AUTH_BASIC_PASSWORD` env vars for HTTP repos

**Maven Artifact Search:**
- search.maven.org Solr API - `https://search.maven.org/solrsearch/select`
- Sonatype Central Solr API - `https://central.sonatype.com/solrsearch/select`
- Client: `src/main/java/dev/jbang/search/SolrArtifactSearch.java`
- Auth: None (public API)

**AI Code Generation (OpenAI-compatible API):**
- OpenAI - `https://api.openai.com/v1` (model: `gpt-5.1`)
- OpenRouter - `https://openrouter.ai/api/v1`
- Google Gemini - `https://generativelanguage.googleapis.com/v1beta/openai`
- GitHub Models - `https://models.github.ai/inference`
- OpenCode Zen - `https://opencode.ai/zen/v1`
- Ollama (local) - `http://localhost:11434/v1`
- Client: `src/main/java/dev/jbang/ai/OpenAIProvider.java`, factory in `src/main/java/dev/jbang/ai/AIProviderFactory.java`
- Auth: `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `GEMINI_API_KEY`, `OPENCODE_API_KEY`, `GITHUB_TOKEN` env vars

**JBang Version Check:**
- `https://www.jbang.dev/releases/latest/download/version.txt` - checked asynchronously on startup
- Client: `src/main/java/dev/jbang/util/VersionChecker.java`
- Auth: None

**JDK Download and Management:**
- Adoptium/Temurin and other JDK distributions via `dev.jbang:devkitman`
- Client: `devkitman` library, invoked from `src/main/java/dev/jbang/cli/Jdk.java`
- Auth: None

**Kotlin Compiler Download:**
- GitHub Releases - `https://github.com/JetBrains/kotlin/releases/download/v{version}/kotlin-compiler-{version}.zip`
- Default version: 2.3.10
- Client: `src/main/java/dev/jbang/net/KotlinManager.java`
- Auth: None

**Groovy Compiler Download:**
- Apache Groovy distribution mirrors
- Default version: 4.0.30
- Client: `src/main/java/dev/jbang/net/GroovyManager.java`
- Auth: None

**VSCodium Editor Download:**
- GitHub Releases - `https://github.com/VSCodium/vscodium/releases/download/...`
- Client: `src/main/java/dev/jbang/net/EditorManager.java`
- Auth: None

**Remote Script/Source Fetching:**
- Any HTTP/HTTPS URL - scripts and sources fetched directly
- GitHub, GitLab, Bitbucket URLs converted to raw content URLs via `src/main/java/dev/jbang/util/Util.java` (`swizzleURL`)
- Trust management for remote sources: `src/main/java/dev/jbang/net/TrustedSources.java`

## Data Storage

**Databases:**
- None (no relational/document database)

**File Storage:**
- Local filesystem only
- Config dir: `~/.jbang/` (override: `JBANG_DIR`)
- Cache dir: `~/.jbang/cache/` (override: `JBANG_CACHE_DIR`)
- Dependency cache JSON: `~/.jbang/cache/dependency_cache.json`
- Trusted sources JSON: `~/.jbang/trusted-sources.json`
- Catalog files: `jbang-catalog.json` discovered hierarchically in project dirs and `~/.jbang/`
- Local Maven repo: `~/.m2/repository` (override: `JBANG_REPO`)
- Client: `src/main/java/dev/jbang/Settings.java`, `src/main/java/dev/jbang/Cache.java`

**Caching:**
- File-based HTTP download cache in `~/.jbang/cache/`
- Dependency resolution cache in `~/.jbang/cache/dependency_cache.json`
- Cache eviction default: 12 hours (`PT12H`), configurable via `cache-evict` config key
- Implementation: `src/main/java/dev/jbang/dependencies/DependencyCache.java`, `src/main/java/dev/jbang/util/NetUtil.java`

## Authentication & Identity

**Auth Provider:**
- No centralized auth provider
- Per-repository HTTP Basic auth via `JBANG_AUTH_BASIC_USERNAME` / `JBANG_AUTH_BASIC_PASSWORD`
- AI providers via individual `*_API_KEY` env vars
- Trust model for remote scripts via `~/.jbang/trusted-sources.json` (`src/main/java/dev/jbang/net/TrustedSources.java`)

## Monitoring & Observability

**Error Tracking:**
- None (no external error tracking service)

**Logs:**
- Console output only via `src/main/java/dev/jbang/util/Util.java` (`infoMsg`, `verboseMsg`, `errorMsg`, `warnMsg`)
- `java.util.logging` (JUL) configured at startup via `src/main/resources/logging.properties`
- SLF4J no-op binding suppresses library log output

**Test Reporting:**
- Allure reports (JUnit 5 listener, `io.qameta.allure:allure-junit5`)
- Allure artifacts published to artifact.ci per CI run
- JaCoCo coverage XML/HTML output to `build/reports/`
- SonarCloud: `sonar.projectKey=jbangdev_jbang`, `sonar.organization=jbangdev`
- Codecov: `codecov.yml` present (informational only, does not gate PRs)

## CI/CD & Deployment

**Hosting:**
- GitHub Releases - primary release artifact distribution
- Maven Central (Sonatype) - published via JReleaser as `dev.jbang:jbang.bin`
- Docker Hub, Quay.io, GHCR - container images `jbangdev/jbang:*`, `jbangdev/jbang-action:*`
- Homebrew, Scoop, Chocolatey, Snap, SDKMAN - package manager distributions via JReleaser

**CI Pipeline:**
- GitHub Actions (`.github/workflows/`)
  - `ci-build.yml` - PR builds (format check, unit tests, integration tests, native image tests)
  - `main-build.yml` - main branch push → early access release via JReleaser
  - `tag-and-release.yml` - tag push → full release (JReleaser: Docker, SDKMAN, Brew, Scoop, etc.)
  - `install-tests.yml` - install verification tests
  - `codeql-analysis.yml` - GitHub CodeQL security scanning
  - `report.yml` - Allure report publishing
- Test matrix: Java 8, 11, 17, 21, 25 on Ubuntu, macOS, Windows (partial matrix)
- Native image tested on all three OS platforms

**Release Tool:**
- JReleaser 1.19.0 (`jreleaser.yml`) handles: GitHub release, Maven Central publish, Docker push, SDKMAN, Brew, Scoop, Chocolatey, Snap, Bluesky announcement

## Environment Configuration

**Required env vars for release (CI secrets):**
- `SDKMAN_CONSUMER_KEY`, `SDKMAN_CONSUMER_TOKEN` - SDKMAN publish
- `BLUESKY_PASSWORD` - Bluesky announcement
- `BREW_GITHUB_TOKEN` - Homebrew formula PR
- `GPG_PASSPHRASE`, `GPG_PUBLIC_KEY`, `GPG_SECRET_KEY` - artifact signing
- `OSSRH_USERNAME`, `OSSRH_TOKEN` - Maven Central publish

**Secrets location:**
- GitHub Actions repository secrets (referenced as `${{ secrets.* }}` in workflow files)
- No `.env` file present in repository

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None (release announcements are push-initiated via JReleaser, not webhook callbacks)
