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

import dev.jbang.Settings;

public class VersionChecker {
	private static final String jbangVersionUrl = "https://www.jbang.dev/releases/latest/download/version.txt";

	private static final long DELAY_DAYS = 3;

	/**
	 * First we determine when was the last time we checked Jbang's latest version
	 * and if enough time has passed (so as not to annoy the user with too frequent
	 * messages) we perform the check and tell the user if a new version is
	 * available. NB: The check is never performed if Quiet mode is enabled.
	 */
	public static void possiblyCheck() {
		if (shouldCheck()) {
			check();
		}
	}

	// Figure out if we should check now or wait for a couple of days
	public static boolean shouldCheck() {
		String noVersion = System.getenv().getOrDefault(Settings.ENV_NO_VERSION_CHECK, "false");
		if (Util.isQuiet() || noVersion.equalsIgnoreCase("true")) {
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
	 * against the latest version on GitHub. If there is a newer version we tell the
	 * user about it and give them instructions on how to update.
	 */
	public static void check() {
		String latestVersion = retrieveLatestVersion();
		if (latestVersion != null) {
			if (compareVersions(latestVersion, Util.getJbangVersion()) > 0) {
				Util.infoMsg("There is a new version of jbang available!");
				Util.infoMsg("You have version " + Util.getJbangVersion()
						+ " and " + latestVersion + " is the latest.");
				if (runningManagedJbang()) {
					Util.infoMsg("Run 'jbang app install --force jbang' to update to the latest version.");
				} else {
					Util.infoMsg("Use your package manager to update jbang to the latest version,");
					Util.infoMsg("or visit https://jbang.dev to download and install it yourself.");
				}
			} else {
				Util.verboseMsg("jbang is up-to-date.");
			}
		}
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
	private static boolean runningManagedJbang() {
		try {
			File jarFile = new File(VersionChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return jarFile.toPath().startsWith(Settings.getConfigBinDir());
		} catch (URISyntaxException e) {
			// ignore
		}
		return false;
	}
}
