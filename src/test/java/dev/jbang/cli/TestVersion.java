package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.util.Util;
import dev.jbang.util.VersionChecker;

public class TestVersion extends BaseTest {

	@BeforeEach
	void setUp() {
		VersionChecker.reset();
	}

	@AfterEach
	void tearDown() {
		VersionChecker.reset();
	}

	@Test
	void testVersionDefaultCall() throws Exception {
		Version versionCmd = new Version();
		Integer result = CommandInvoker.validateAndCall(versionCmd);
		assertThat(result, equalTo(ExitException.EXIT_OK));
		assertFalse(versionCmd.update);
		assertFalse(versionCmd.checkForUpdate);
		assertFalse(versionCmd.force);
	}

	@Test
	void testVersionCheckCall() throws Exception {
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(Util.getJBangVersion()));
		Version versionCmd = new Version();
		versionCmd.checkForUpdate = true;
		Integer result = CommandInvoker.validateAndCall(versionCmd);
		assertThat(result, equalTo(ExitException.EXIT_OK));
		assertTrue(VersionChecker.isInformed());
	}

	@Test
	void testVersionUpdateWhenUpToDate() throws Exception {
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(Util.getJBangVersion()));
		Version versionCmd = new Version();
		versionCmd.update = true;
		Integer result = CommandInvoker.validateAndCall(versionCmd);
		assertThat(result, equalTo(ExitException.EXIT_OK));
		assertTrue(VersionChecker.isInformed());
	}

	@Test
	void testVersionUpdateWhenPendingLauncherExists() throws Exception {
		if (Util.isWindows() && Util.getShell() == Util.Shell.cmd) {
			Path binDir = Settings.getConfigBinDir();
			binDir.toFile().mkdirs();
			Path launcherUpdate = binDir.resolve("jbang.cmd.new");
			Files.write(launcherUpdate, Collections.singletonList("echo test"));
			try {
				Version versionCmd = new Version();
				versionCmd.update = true;
				Integer result = CommandInvoker.validateAndCall(versionCmd);
				assertThat(result, equalTo(ExitException.EXIT_EXECUTE));
			} finally {
				Files.deleteIfExists(launcherUpdate);
			}
		}
	}
}
