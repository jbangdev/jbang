package dev.jbang.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.jbang.Settings;

public class VersionChecker {
	private static final String jbangVersionUrl = "https://www.jbang.dev/releases/latest/download/version.txt";

	private static final long DELAY_DAYS = 3;

	public static CompletableFuture<String> newerVersionAsync() {
		return CompletableFuture.supplyAsync(() -> newerVersion());
	}

	/**
	 * First we determine when was the last time we checked Jbang's latest version
	 * and if enough time has passed (so as not to annoy the user with too frequent
	 * messages) we perform the check and return the new version number, otherwise
	 * we return `null`. NB: The check is never performed if Quiet mode is enabled.
	 */
	public static String newerVersion() {
		if (shouldCheck()) {
			return retrieveLatestVersion();
		}
		return null;
	}

	// Figure out if we should check now or wait for a couple of days
	public static boolean shouldCheck() {
		String noVersion = System.getenv().getOrDefault(Settings.ENV_NO_VERSION_CHECK, "false");
		if (Util.isOffline() || Util.isQuiet() || !noVersion.equalsIgnoreCase("false")) {
			return false;
		}
		try {
			Path versionFile = Settings.getCacheDir().resolve("version.txt");
			if (Files.isRegularFile(versionFile)) {
				FileTime ts = Files.getLastModifiedTime(versionFile);
				long diff = Duration.between(ts.toInstant(), Instant.now()).toDays();
				return diff > DELAY_DAYS;
			}
		} catch (IOException e) {
			// ignore
		}
		return true;
	}

	/**
	 * Checks if we're running the latest version of Jbang by comparing its version
	 * against the latest version on GitHub.
	 */
	public static boolean isNewer(String latestVersion) {
		return (latestVersion != null && compareVersions(latestVersion, Util.getJbangVersion()) > 0);
	}

	// Determines and returns the latest Jbang version from GitHub
	private static String retrieveLatestVersion() {
		try {
			Path versionFile = Util.downloadFile(jbangVersionUrl, Settings.getCacheDir().toFile(), 2000);
			List<String> lines = Files.readAllLines(versionFile);
			if (!lines.isEmpty()) {
				return lines.get(0);
			}
		} catch (IOException e) {
			Util.verboseMsg("Couldn't determine latest Jbang version", e);
		}
		return null;
	}

	private static int compareVersions(String v1, String v2) {
		String[] v1p = v1.split("\\.");
		String[] v2p = v2.split("\\.");
		int maxl = Math.max(v1p.length, v2p.length);
		for (int i = 0; i < maxl; i++) {
			int v1n = safeParseNum(v1p, i);
			int v2n = safeParseNum(v2p, i);
			if (v1n > v2n) {
				return 1;
			} else if (v1n < v2n) {
				return -1;
			}
		}
		return 0;
	}

	private static int safeParseNum(String[] versionParts, int i) {
		if (i < versionParts.length) {
			String num = versionParts[i];
			try {
				return Integer.parseUnsignedInt(num);
			} catch (NumberFormatException ex) {
				return -1;
			}
		}
		return 0;
	}

	// Determines if the current Jbang we're running was one installed using
	// `app install` or not
	public static boolean runningManagedJbang() {
		try {
			File jarFile = new File(VersionChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return jarFile.toPath().startsWith(Settings.getConfigBinDir());
		} catch (URISyntaxException e) {
			// ignore
		}
		return false;
	}
}
