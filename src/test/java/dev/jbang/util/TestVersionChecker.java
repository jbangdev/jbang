package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestVersionChecker extends BaseTest {

	@BeforeEach
	void setUp() {
		VersionChecker.reset();
	}

	@AfterEach
	void tearDown() {
		VersionChecker.reset();
		Util.setRunningManagedJBang(null);
	}

	@Test
	void testCompareVersions() {
		assertTrue(VersionChecker.compareVersions("0.141.0", "0.139.3") > 0);
		assertTrue(VersionChecker.compareVersions("0.139.3", "0.141.0") < 0);
		assertEquals(0, VersionChecker.compareVersions("0.141.0", "0.141.0"));
		assertTrue(VersionChecker.compareVersions("0.141.0", "0.141.0-SNAPSHOT") > 0);
		assertTrue(VersionChecker.compareVersions("0.141.1", "0.141.0") > 0);
		assertTrue(VersionChecker.compareVersions("1.0.0", "0.141.0") > 0);
	}

	@Test
	void testIsNewer() {
		String current = Util.getJBangVersion();
		assertFalse(VersionChecker.isNewer(current));
		assertFalse(VersionChecker.isNewer(null));
		assertTrue(VersionChecker.isNewer("999.0.0"));
		assertFalse(VersionChecker.isNewer("0.0.1"));
	}

	@Test
	void testUpdateOrInformWhenUpToDateManaged() {
		Util.setRunningManagedJBang(true);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(Util.getJBangVersion()));

		boolean shouldUpdate = VersionChecker.updateOrInform();
		assertFalse(shouldUpdate, "Should not update when already up to date");
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true");
	}

	@Test
	void testUpdateOrInformWhenNewerVersionManaged() {
		Util.setRunningManagedJBang(true);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture("999.0.0"));

		boolean shouldUpdate = VersionChecker.updateOrInform();
		assertTrue(shouldUpdate, "Should update when newer version is available");
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true to suppress duplicate prompts");
		assertEquals("999.0.0", VersionChecker.getLatestVersion(), "Should return the latest version");
	}

	@Test
	void testUpdateOrInformUnmanaged() {
		Util.setRunningManagedJBang(false);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(Util.getJBangVersion()));

		boolean shouldUpdate = VersionChecker.updateOrInform();
		assertFalse(shouldUpdate, "Unmanaged jbang should not perform automated install");
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true");
	}

	@Test
	void testUpdateOrInformWithForce() {
		Util.setRunningManagedJBang(true);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(Util.getJBangVersion()));

		boolean shouldUpdate = VersionChecker.updateOrInform(true);
		assertTrue(shouldUpdate, "Should update when force is true even if already up to date");
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true");
	}

	@Test
	void testUpdateOrInformUnmanagedOffline() {
		Util.setRunningManagedJBang(false);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(null));

		boolean shouldUpdate = VersionChecker.updateOrInform(false);
		assertFalse(shouldUpdate, "Should not update when latest version is null");
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true");
	}

	@Test
	void testCheckNowAndInformUnmanagedOffline() {
		Util.setRunningManagedJBang(false);
		VersionChecker.setVersionCheckResult(CompletableFuture.completedFuture(null));

		VersionChecker.checkNowAndInform();
		assertTrue(VersionChecker.isInformed(), "Should mark informed as true for unmanaged install when offline");
	}
}
