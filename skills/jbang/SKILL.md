---
name: jbang
description: Run and share single-file Java and JShell programs (plus experimental Kotlin, Groovy and Markdown), jars and Maven GAVs with JBang - no project, no build file, no preinstalled JDK. Covers installing JBang itself (including as a GraalVM native binary via JBANG_USE_NATIVE on Linux, macOS and Windows), running scripts and catalog aliases, installing commands on the PATH, building native executables from a script, and running JBang unattended in CI.
license: MIT
---

# JBang

`jbang` compiles and runs a single source file, a jar, or a Maven coordinate directly. It downloads a JDK if none is present, resolves `//DEPS` dependencies, and caches builds — so a script is a file, not a project.

```bash
jbang init --template=cli hello.java   # scaffold
jbang hello.java Max!                  # compile + run (run is the default command)
jbang gavsearch@jbangdev picocli       # run an alias from a catalog
jbang org.example:app:1.0.0            # run a Maven GAV
```

## Install JBang

Bootstrap (Linux/macOS/WSL/AIX bash) — `app setup` puts `jbang` on the `PATH` and adds a `j!` alias:

```bash
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

Windows PowerShell:

```powershell
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"
```

Package managers: `sdk install jbang`, `brew install jbangdev/tap/jbang`, `choco install jbang`, `scoop install jbang`, `asdf install jbang latest`, `npx @jbangdev/jbang`, `uvx jbang`. All of these install the **JAR** distribution and therefore need a JVM to start; see the next section for the native binary.

Everything lives under `~/.jbang` (`JBANG_DIR`), so uninstalling is deleting that directory.

## Install JBang as a native binary (`JBANG_USE_NATIVE`)

The release also ships a GraalVM native-image build of JBang itself. It starts faster and needs no JVM to launch JBang — useful on machines with no JDK, in slim containers, and in CI.

The launcher scripts (`jbang`, `jbang.cmd`, `jbang.ps1`) read `JBANG_USE_NATIVE` **on every invocation**, not just at install time:

- `true` → run `~/.jbang/bin/jbang.bin-<os>-<arch>` (`.exe` on Windows), and skip JDK detection entirely.
- unset/`false` → run `~/.jbang/bin/jbang.jar` with a JVM (downloading a JDK first if none is found).

So export it in your shell profile / user environment, not only in the install command.

### Supported platforms

| Platform | Native bundle | Native supported |
|---|---|---|
| Linux x64 (glibc) | `jbang-linux-x64.tar` / `.zip` | yes |
| macOS Apple Silicon | `jbang-mac-aarch64.tar` / `.zip` | yes |
| Windows x64 | `jbang-windows-x64.zip` | yes |
| macOS Intel, Linux aarch64, Windows ARM64, Alpine/musl, AIX | — | no — leave `JBANG_USE_NATIVE` unset |

On an unsupported platform the launcher builds a bundle name that does not exist and the install fails with a download error (HTTP 404). There is no automatic fallback to the JAR bundle.

### Linux / macOS

```bash
export JBANG_USE_NATIVE=true
curl -Ls https://sh.jbang.dev | bash -s - app setup

echo 'export JBANG_USE_NATIVE=true' >> ~/.bashrc   # or ~/.zshrc — must persist
```

### Windows (PowerShell)

```powershell
$env:JBANG_USE_NATIVE = 'true'
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"

# persist for future sessions (user scope)
[Environment]::SetEnvironmentVariable('JBANG_USE_NATIVE', 'true', 'User')
```

`jbang.cmd` honours the same variable, so CMD users can `setx JBANG_USE_NATIVE true` instead.

### Manual install

Unpack `jbang-<version>-<os>-<arch>.zip` from the [releases page](https://github.com/jbangdev/jbang/releases/latest), add its `bin/` to the `PATH` and export `JBANG_USE_NATIVE=true`. A binary placed there by hand may also be named plainly `jbang.bin` / `jbang.bin.exe` — the scripts try `jbang.bin-<os>-<arch>` first and fall back to that.

### Verify

```bash
jbang version --verbose
# Java: null [25.0.2]
# Native Image: true
```

`Native Image: false` means the launcher used the JAR — see the pitfalls below.

### Switching an existing JAR install to native

Setting the variable alone does **not** upgrade an existing installation: the launcher finds `~/.jbang/bin/jbang.jar`, skips the download, and keeps using the JAR (after printing `WARNING: JBang native binary ... not found`). Delete that JAR to force a re-bootstrap — the next run with the variable set fetches the native bundle:

```bash
rm -f ~/.jbang/bin/jbang.jar
export JBANG_USE_NATIVE=true
jbang version --verbose          # re-downloads, then prints: Native Image: true
```

```powershell
Remove-Item "$env:USERPROFILE\.jbang\bin\jbang.jar"
$env:JBANG_USE_NATIVE = 'true'
jbang version --verbose
```

Do **not** delete the whole `~/.jbang/bin` directory: `jbang app install` puts installed commands there. The re-bootstrap replaces only the `jbang*` files and leaves installed apps, JDKs and caches alone.

### Pitfalls

- **The variable must stay set.** The native bundle contains the JAR too, so a shell without `JBANG_USE_NATIVE=true` silently falls back to the JAR and pulls in a JDK.
- **Package-manager installs have no native binary.** SDKMAN, Homebrew, Chocolatey, Scoop, COPR, npm and pipx ship the JAR only; with the variable set they warn on *every* run and use the JAR anyway. Use the bootstrap script or the manual install for native.
- **Native JBang still needs a JDK to build your code.** It removes the JVM requirement for JBang itself, not for compiling and running Java scripts — that JDK is downloaded on demand (`jbang jdk list` / `jbang jdk install 21`).
- **Early-access builds** combine the two variables: `JBANG_DOWNLOAD_VERSION=early-access JBANG_USE_NATIVE=true`. Upstream still labels the native build experimental there — if something misbehaves, unsetting the variable falls back to the JAR from the same installation.

## Everyday commands

| Command | Purpose |
|---|---|
| `jbang <file\|url\|gav\|alias>` | Build (cached) and run; `run` is the default command |
| `jbang init --template=cli app.java` | Scaffold a script from a template |
| `jbang edit --open=code --live app.java` | Open a temporary IDE project with live sync |
| `jbang deps add\|search app.java` | Add or look up `//DEPS` entries |
| `jbang app install --name=hello app.java` | Put a script on the `PATH` as a command |
| `jbang app install --force <alias>@<catalog>` | (Re)install from a catalog, e.g. `jabkit@jabref` |
| `jbang app list\|uninstall` | Manage installed commands |
| `jbang jdk list\|install\|default` | Manage JBang-provisioned JDKs |
| `jbang export fatjar\|native\|maven\|gradle app.java` | Turn a script into a distributable artifact or project |
| `jbang cache clear` | Drop cached builds, JDKs and downloads |
| `jbang wrapper install` | Commit a `./jbang` wrapper into a repository |

Useful global flags: `--fresh` (bypass caches), `--offline`, `--quiet`, `--verbose`, `--stacktrace`.

## Native executables *of your script* (not the same thing)

`JBANG_USE_NATIVE` selects how JBang itself runs. To compile **your** script into a native executable, use `-n`/`--native`, which requires a GraalVM JDK:

```bash
jbang --native app.java            # build + run natively
jbang app install --native app.java
jbang export native app.java       # standalone binary
```

## Unattended / CI use

- Pre-trust remote sources: an untrusted URL triggers an interactive prompt and aborts with exit code 10 if unanswered. `jbang trust add https://github.com/<org>/` (or a full `*` wildcard, which disables the protection) before the run.
- Pin the version with `JBANG_DOWNLOAD_VERSION=0.141.0`; mirror downloads with `JBANG_DOWNLOAD_URL`; harden flaky networks with `JBANG_DOWNLOAD_RETRY` / `JBANG_DOWNLOAD_RETRY_DELAY`.
- Isolate installs by pointing `JBANG_DIR` at a scratch directory — useful for testing an install recipe without touching `~/.jbang`.
- Silence the daily update check with `JBANG_NO_VERSION_CHECK=true`; `--offline` skips it too.
- GitHub Actions: `uses: jbangdev/setup-jbang@main`. Containers: `jbangdev/jbang-action`.

Full reference: [installation](https://jbang.dev/documentation/jbang/latest/installation.html) (including the complete startup-script environment variable table), [running](https://jbang.dev/documentation/jbang/latest/running.html), [troubleshooting](https://jbang.dev/documentation/jbang/latest/troubleshooting.html).
