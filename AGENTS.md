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
- Commits: use conventional/semantic format — `feat:`, `fix:`, `build:`, `docs:`, etc. PR titles follow the same convention.
- Startup scripts live in `src/main/scripts/`: `jbang` (bash), `jbang.cmd` (CMD), `jbang.ps1` (PowerShell). The CMD script delegates downloads and JDK installs to `jbang.ps1`. Changes affecting downloads or bootstrap must be applied consistently across all three. Behavior (e.g., retry backoff) must be consistent across tools — watch for tool-specific quirks like `curl --retry-delay 0` meaning exponential backoff while `wget --waitretry=0` meaning no delay.
- Environment variables follow `JBANG_*` naming. New env vars must have defaults in each script that actually uses them (e.g., `jbang.cmd` delegates downloads to `jbang.ps1`, so download-related vars only need defaults in `jbang` and `jbang.ps1`). Document new vars in `installation.adoc` ("Startup Script Environment Variables") and ensure consistent behavior across platforms.
- Documentation is AsciiDoc under `docs/modules/ROOT/pages/` (e.g., `installation.adoc`, `troubleshooting.adoc`).
- New features, CLI options, and environment variables **must** include documentation updates. When reviewing a PR, always check that user-facing changes have corresponding doc updates in `docs/modules/ROOT/pages/`. Key pages: `configuration.adoc` (options, auth, proxies), `installation.adoc` (env vars, setup), `running.adoc` (runtime behavior), `troubleshooting.adoc`.
- Test infrastructure: `BaseTest` includes WireMock for HTTP mocking (records/replays requests). `BaseIT` provides `shell()` helpers for running CLI commands. Use `assumeTrue` for conditionally skipping tests (e.g., `assumeTrue(isCommandAvailable("bash"))` or `assumeTrue(isCommandAvailable("pwsh"))`).
- Script tests should run the real scripts from `src/main/scripts/` — not synthetic copies or extracted functions. Use env var overrides (e.g., `JBANG_DOWNLOAD_URL`) to point real scripts at WireMock.
- Authentication/credential chain: most-specific source wins. Order: URL userinfo → `.netrc` exact host → `GITHUB_TOKEN`/`GITLAB_TOKEN` → `.netrc` default → `JBANG_AUTH_BASIC_*`. For Maven repos, `settings.xml` `<server>` entries override all of the above when the server `<id>` matches. Shared logic lives in `NetUtil.getCredentialsForHost()`; don't duplicate the chain — call the shared method.
- Security: never auto-escalate credentials to parent domains (e.g., don't send `github.com` credentials to `evil.github.com`). Credential lookup is exact-host only.
- `.netrc` extensions: JBang-specific keys (e.g., `jbang-auth`) are namespaced to avoid collision with standard netrc fields. Other tools silently ignore them.
- External process calls (e.g., `git credential fill`, `gh auth token`): always set a timeout, cache results per-host for the process lifetime, and fail gracefully (fall through to next auth source on error).

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:46cd31e7 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   bd dolt push
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->

<!-- BEGIN BEADS CODEX SETUP: generated by bd setup codex -->
## Beads Issue Tracker

Use Beads (`bd`) for durable task tracking in repositories that include it. Use the `beads` skill at `.agents/skills/beads/SKILL.md` (project install) or `~/.agents/skills/beads/SKILL.md` (global install) for Beads workflow guidance, then use the `bd` CLI for issue operations.

### Quick Reference

```bash
bd ready                # Find available work
bd show <id>            # View issue details
bd update <id> --claim  # Claim work
bd close <id>           # Complete work
bd prime                # Refresh Beads context
```

### Rules

- Use `bd` for all task tracking; do not create markdown TODO lists.
- Run `bd prime` when Beads context is missing or stale. Codex 0.129.0+ can load Beads context automatically through native hooks; use `/hooks` to inspect or toggle them.
- Keep persistent project memory in Beads via `bd remember`; do not create ad hoc memory files.

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.
<!-- END BEADS CODEX SETUP -->
