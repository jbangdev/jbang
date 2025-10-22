# JBang Agent Handbook

- Toolchain: Gradle build, Java 11 runtime (targets 8 bytecode).
- Build everything: `./gradlew build`; prefer Gradle tasks over direct javac.
- Unit tests: `./gradlew test`; single test with `./gradlew test --tests "pkg.Class"`.
- Always add unit tests, and if relevant integration tests for new features and bugfixes.
- Integration tests: `./gradlew integrationTest`; filter via `--tests "pkg.ITClass"`.
- Formatting: `./gradlew spotlessApply`; verify using `spotlessCheck`.
- No extra linters; rely on compiler + spotless for CI hygiene.
- Source layout: app in `src/main/java`, unit tests in `src/test/java`, IT in `src/it/java`.
- Main entry point: `dev.jbang.Main`; CLI commands built with picocli.
- Keep packages under `dev.jbang`; match existing folder hierarchy.
- Imports ordered java → javax → org → com → dev.jbang → blank line.
- Drop unused imports; never use wildcard or static-on-demand imports.
- Formatting uses `misc/eclipse_formatting_nowrap.xml`; indent 4 spaces, no wrapping.
- Naming: UpperCamelCase types, lowerCamelCase members, UPPER_SNAKE constants.
- Types: prefer explicit generics; annotate nullability with `@jspecify` where applicable.
- Avoid raw collections and unchecked casts; keep method signatures explicit.
- Error handling: throw `dev.jbang.cli.ExitException` for controlled exits; let picocli report parameter issues.
- Logging/output: use `dev.jbang.util.Util` helpers (e.g., `infoMsg`, `verboseMsg`).
