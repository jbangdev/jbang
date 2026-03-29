---
updated: 2026-03-29
focus: quality
---
# Testing Patterns

**Analysis Date:** 2026-03-29

## Test Framework

**Runner:**
- JUnit Jupiter (JUnit 5) — `org.junit.jupiter:junit-jupiter` via `junit-bom:5.14.1`
- Config: `build.gradle` (`testing { suites { test { useJUnitJupiter() } } }`)

**Assertion Libraries:**
- Hamcrest (`org.hamcrest:hamcrest-library:2.2`) — primary in unit tests
- AssertJ (`org.assertj:assertj-core:3.27.7`) — primary in integration tests
- JUnit 5 `Assertions` (`assertEquals`, `assertThrows`, `assertTrue`) — used alongside Hamcrest

**Reporting:**
- Allure (`io.qameta.allure:allure-junit5`) with AspectJ weaver agent for rich test reports
- Reports generated to `build/reports/allure-report`

**Run Commands:**
```bash
./gradlew test                    # Run unit tests
./gradlew integrationTest         # Run integration tests (requires installDist first)
./gradlew test integrationTest    # Run both
./gradlew test -PdisableWiremock=true  # Run unit tests without WireMock
./gradlew test -PtestJavaVersion=17 -PtestJavaVendor=adoptium  # Test with specific JDK
```

## Test File Organization

**Location:**
- Unit tests: `src/test/java/dev/jbang/` — parallel package structure to `src/main/java/dev/jbang/`
- Integration tests: `src/it/java/dev/jbang/it/` — flat package, all in `dev.jbang.it`
- Test resources/fixtures: `src/test/resources/wiremock/` — WireMock recording/replay mappings
- Example scripts used by tests: `itests/` (root directory, copied to `build/classes/java/test/itests`)

**Naming:**
- Unit tests: `Test{ClassName}.java` — `TestSource.java`, `TestProjectBuilder.java`, `TestAlias.java`
- Integration tests: `{Feature}IT.java` — `RunIT.java`, `AliasIT.java`, `DependenciesIT.java`

**Structure:**
```
src/
├── test/
│   └── java/dev/jbang/
│       ├── BaseTest.java              # Abstract base for all unit tests
│       ├── JBangTestExecutionListener.java  # Global setup/teardown hook
│       ├── cli/                       # Tests for CLI commands
│       ├── source/                    # Tests for source/build pipeline
│       ├── dependencies/              # Tests for dependency resolution
│       ├── net/                       # Tests for network utilities
│       └── util/                      # Tests for utility classes
└── it/
    └── java/dev/jbang/it/
        ├── BaseIT.java                # Abstract base for all integration tests
        ├── CommandResult.java         # Value object for shell command output
        ├── CommandResultAssert.java   # Custom AssertJ assert for CommandResult
        └── *IT.java                   # Feature integration tests
```

## Test Structure

**Unit Test Suite Organization:**

All unit tests extend `BaseTest` (`src/test/java/dev/jbang/BaseTest.java`):

```java
public class TestProjectBuilder extends BaseTest {

    @Test
    void testDuplicateAnonRepos() {
        ProjectBuilder pb = Project.builder();
        pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
        Path src = examplesTestFolder.resolve("quote.java");
        Project prj = pb.build(src);
        assertThrows(ExitException.class, () -> {
            BuildContext.forProject(prj).resolveClassPath();
        });
    }
}
```

**Integration Test Suite Organization:**

All integration tests extend `BaseIT` (`src/it/java/dev/jbang/it/BaseIT.java`):

```java
public class RunIT extends BaseIT {

    @Test
    @Description("Testing that jbang is running in a clean environment")
    public void testIsolation() {
        assertThat(shell("jbang version --verbose"))
            .errContains("Cache: " + scratch().toString())
            .succeeded();
    }
}
```

**Patterns:**
- `@BeforeEach` in `BaseTest` sets up a fresh isolated temp directory per test (fresh JBang config, cache, cwd)
- `@BeforeAll` equivalent runs via `JBangTestExecutionListener` implementing `TestExecutionListener` — fires once globally across the entire test run, managing shared Maven and JDK temp directories
- `@AfterEach` in `BaseTest` cleans up WireMock
- `@TempDir` injection from JUnit 5 used for test-local filesystem isolation
- Tests that need to capture stdout/stderr use `BaseTest.captureOutput(Callable)` or `BaseTest.checkedRun(...)`

## Mocking

**Framework:** WireMock (`org.wiremock:wiremock:3.13.2`) — HTTP proxy record/replay

**Patterns:**

WireMock is started as a full MITM proxy in `BaseTest.initWireMock()`:

```java
globalwms = new WireMockServer(options()
    .caKeystorePath("misc/wiremock.jks")
    .enableBrowserProxying(true)
    .withRootDirectory("src/test/resources/wiremock")
    .dynamicPort());
globalwms.start();
JvmProxyConfigurer.configureFor(globalwms);
```

- Recorded mappings live in `src/test/resources/wiremock/` and are committed to git
- New network requests made by tests are recorded automatically during local development
- In CI (`CI=true`), any unmatched (unrecorded) request causes the test to fail, ensuring tests stay hermetic
- WireMock can be disabled via `-PdisableWiremock=true` Gradle flag or `jbang.test.wiremock.enable=false` system property
- No Mockito or similar mocking framework is used — tests use real implementations with isolated environments

**What to Mock:**
- External HTTP calls (GitHub, Maven Central, etc.) — captured via WireMock

**What NOT to Mock:**
- File system operations — use `@TempDir` and the isolated temp environment from `BaseTest`
- JBang's own classes — test with real implementations

## Fixtures and Factories

**Test Data:**

The `itests/` directory at the project root contains real Java/Kotlin/Groovy source files used as test fixtures:

```
itests/
├── helloworld.java        # Standard hello world
├── quote.java             # Script with quoting edge cases
├── classpath_example.java # Script with dependencies
├── dualclass.java         # Multi-class source
├── RootOne.java           # Multi-file source root
└── ...
```

Tests reference these via `BaseTest.examplesTestFolder`:
```java
Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
```

**Inline fixtures** — many tests construct source code as Java strings inline.

**Location:**
- Physical fixture scripts: `itests/` (root)
- WireMock HTTP recording stubs: `src/test/resources/wiremock/`
- WAR-specific test fixtures: `src/test/java/dev/jbang/util/WarTestFixtures.java`

## Coverage

**Requirements:** No enforced minimum threshold

**JaCoCo** is configured but disabled on the standard `test` task (`jacoco { enabled = false }`). Coverage is generated separately:

```bash
./gradlew jacocoTestReport   # Generate coverage report after running tests
open build/reports/jacoco/test/html/index.html
```

SonarCloud (`sonarcloud.io`) is configured for remote analysis via `./gradlew sonarqube`.

## Test Types

**Unit Tests (`src/test/`):**
- Scope: individual classes, methods, and small integrated subsystems
- Run entirely in-process — no subprocess spawning
- WireMock proxies all outbound HTTP for hermetic network behavior
- Use isolated temp directories via `BaseTest` setup
- Cover: CLI argument parsing, source parsing, build pipeline, catalog/alias resolution, dependency logic, utilities

**Integration Tests (`src/it/`):**
- Scope: full end-to-end scenarios using the installed `jbang` binary
- Spawn real OS processes via `zeroturnaround/zt-exec`'s `ProcessExecutor`
- Use `CommandResultAssert` (AssertJ custom assert) for fluent assertions on stdout/stderr/exit code
- Require `installDist` to run (Gradle dependency via `tasks.named('integrationTest') { dependsOn("installDist") }`)
- Cover: actual `jbang run`, `jbang alias`, `jbang export`, JDK management, quoting, stdin, etc.
- Support Testcontainers (`org.testcontainers:testcontainers:2.0.2`) for container-based scenarios

## Common Patterns

**Capturing CLI output in unit tests:**
```java
CaptureResult<Integer> result = checkedRun(null, "run", "helloworld.java");
assertThat(result.out, containsString("Hello"));
assertThat(result.result, is(0));
```

**Asserting shell output in integration tests:**
```java
assertThat(shell("jbang notthere.java"))
    .errContains("Script or alias could not be found or read: 'notthere.java'")
    .exitedWith(2);
```

**Asserting exceptions:**
```java
assertThrows(ExitException.class, () -> {
    BuildContext.forProject(prj).resolveClassPath();
});
```

**Conditional disabling:**
```java
@Disabled("twitter stopped supporting non-javascript get")
@DisabledOnOs(OS.WINDOWS)
@DisabledOnJre(JRE.JAVA_8)
@Issue("https://github.com/jbangdev/jbang/issues/2330")
```

**Allure annotations for integration tests:**
```java
@Description("Testing that jbang is running in a clean environment")
public void testIsolation() { ... }
```
