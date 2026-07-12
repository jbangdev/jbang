package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Shared infrastructure for functional tests of JBang startup scripts. Provides
 * WireMock lifecycle, process execution helpers, archive creation utilities,
 * and base environment maps for bash and PowerShell tests.
 */
abstract class AbstractScriptTest {

	protected static final Path BASH_SCRIPT = Paths.get("src/main/scripts/jbang").toAbsolutePath();
	protected static final Path PS1_SCRIPT = Paths.get("src/main/scripts/jbang.ps1").toAbsolutePath();

	protected WireMockServer wm;
	protected String psCommand;

	@TempDir
	protected Path tempDir;

	@BeforeEach
	void startWireMock() {
		wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		wm.start();
	}

	@AfterEach
	void stopWireMock() {
		if (wm != null) {
			wm.stop();
		}
	}

	/**
	 * Creates a unique subdirectory path under {@link #tempDir}. The directory is
	 * not created on disk — startup scripts will create it as needed.
	 */
	protected Path tempSubDir(String name) {
		return tempDir.resolve(name);
	}

	// -------------------------------------------------------------------------
	// Command availability checks
	// -------------------------------------------------------------------------

	protected static boolean isCommandAvailable(String command) {
		try {
			Process p = new ProcessBuilder(command, "--version")
				.redirectErrorStream(true)
				.start();
			p.getInputStream().transferTo(new ByteArrayOutputStream());
			return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	protected void requireBash() {
		assumeTrue(isCommandAvailable("bash"), "bash is not available");
	}

	protected void requirePowerShell() {
		if (isCommandAvailable("pwsh")) {
			psCommand = "pwsh";
		} else if (isCommandAvailable("powershell")) {
			psCommand = "powershell";
		} else {
			assumeTrue(false, "PowerShell is not available (neither pwsh nor powershell found)");
		}
	}

	// -------------------------------------------------------------------------
	// Process execution
	// -------------------------------------------------------------------------

	protected static RunResult runProcess(List<String> cmd, Map<String, String> env) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.environment().putAll(env);
		pb.redirectErrorStream(false);
		Process process = pb.start();

		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		Thread t1 = new Thread(() -> {
			try {
				process.getInputStream().transferTo(stdout);
			} catch (Exception e) {
				/* ignore */ }
		});
		Thread t2 = new Thread(() -> {
			try {
				process.getErrorStream().transferTo(stderr);
			} catch (Exception e) {
				/* ignore */ }
		});
		t1.start();
		t2.start();

		boolean finished = process.waitFor(120, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
		}
		t1.join(5000);
		t2.join(5000);
		assertTrue(finished, "script timed out");
		return new RunResult(process.exitValue(),
				stdout.toString(StandardCharsets.UTF_8),
				stderr.toString(StandardCharsets.UTF_8));
	}

	static class RunResult {
		final int exitCode;
		final String stdout;
		final String stderr;

		RunResult(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	// -------------------------------------------------------------------------
	// Archive creation
	// -------------------------------------------------------------------------

	/**
	 * Creates a minimal jbang.tar containing jbang/bin/jbang (a dummy script that
	 * just exits 0) and an empty jbang/bin/jbang.jar.
	 */
	protected byte[] createJbangTar() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
			byte[] script = "#!/bin/bash\nexit 0\n".getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry entry = new TarArchiveEntry("jbang/bin/jbang");
			entry.setSize(script.length);
			entry.setMode(0755);
			tar.putArchiveEntry(entry);
			tar.write(script);
			tar.closeArchiveEntry();

			TarArchiveEntry jarEntry = new TarArchiveEntry("jbang/bin/jbang.jar");
			jarEntry.setSize(0);
			tar.putArchiveEntry(jarEntry);
			tar.closeArchiveEntry();
		}
		return baos.toByteArray();
	}

	protected byte[] createNativeJbangTar(String version, String os, String arch) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
			byte[] binary = "#!/bin/bash\necho native-companion\n".getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry entry = new TarArchiveEntry("jbang-" + version + "/bin/jbang.bin-" + os + "-" + arch);
			entry.setSize(binary.length);
			entry.setMode(0755);
			tar.putArchiveEntry(entry);
			tar.write(binary);
			tar.closeArchiveEntry();
		}
		return baos.toByteArray();
	}

	/**
	 * Creates a minimal jbang.zip containing jbang/bin/jbang.ps1 (a dummy script
	 * that just exits 0), an empty jbang/bin/jbang.jar, and jbang/bin/jbang.cmd.
	 */
	protected byte[] createJbangZip() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(baos)) {
			zip.putNextEntry(new ZipEntry("jbang/bin/jbang.ps1"));
			zip.write("exit 0\n".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();

			zip.putNextEntry(new ZipEntry("jbang/bin/jbang.jar"));
			zip.closeEntry();

			zip.putNextEntry(new ZipEntry("jbang/bin/jbang.cmd"));
			zip.write("@exit /b 0\r\n".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return baos.toByteArray();
	}

	// -------------------------------------------------------------------------
	// Base environment maps
	// -------------------------------------------------------------------------

	/**
	 * Returns a base environment map for bash tests with JBANG_DIR,
	 * JBANG_CACHE_DIR, and JBANG_NO_VERSION_CHECK set. JAVA_HOME is removed.
	 * Subclasses should add their specific env vars on top.
	 */
	protected Map<String, String> baseBashEnv(String suffix) {
		Path jbdir = tempSubDir("jbdir-" + suffix);
		Path tdir = tempSubDir("cache-" + suffix);
		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("JBANG_DIR", jbdir.toString());
		env.put("JBANG_CACHE_DIR", tdir.toString());
		env.put("JBANG_NO_VERSION_CHECK", "true");
		env.remove("JAVA_HOME");
		return env;
	}

	/**
	 * Returns a base environment map for PowerShell tests with JBANG_DIR,
	 * JBANG_CACHE_DIR, and JBANG_NO_VERSION_CHECK set. JAVA_HOME is removed.
	 * Subclasses should add their specific env vars on top.
	 */
	protected Map<String, String> basePsEnv(String suffix) {
		Path jbdir = tempSubDir("jbdir-" + suffix);
		Path tdir = tempSubDir("cache-" + suffix);
		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("JBANG_DIR", jbdir.toString());
		env.put("JBANG_CACHE_DIR", tdir.toString());
		env.put("JBANG_NO_VERSION_CHECK", "true");
		env.remove("JAVA_HOME");
		return env;
	}

	// -------------------------------------------------------------------------
	// Command builders
	// -------------------------------------------------------------------------

	/**
	 * Builds a command list for running the bash startup script.
	 */
	protected List<String> bashCmd(String... args) {
		List<String> cmd = new ArrayList<>();
		cmd.add("bash");
		cmd.add(BASH_SCRIPT.toString());
		for (String arg : args) {
			cmd.add(arg);
		}
		return cmd;
	}

	/**
	 * Builds a command list for running the PowerShell startup script. Requires
	 * {@link #requirePowerShell()} to have been called first.
	 */
	protected List<String> psCmd(String... args) {
		List<String> cmd = new ArrayList<>();
		cmd.add(psCommand);
		cmd.add("-NoProfile");
		cmd.add("-ExecutionPolicy");
		cmd.add("Bypass");
		cmd.add("-File");
		cmd.add(PS1_SCRIPT.toString());
		for (String arg : args) {
			cmd.add(arg);
		}
		return cmd;
	}
}
