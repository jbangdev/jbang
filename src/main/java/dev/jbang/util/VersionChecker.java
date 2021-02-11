package dev.jbang.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;

public class VersionChecker {
	private static final String jbangVersionUrl = "https://www.jbang.dev/releases/latest/download/version.txt";

	private static final long DELAY_DAYS = 3;
	public static final int CONNECT_TIMEOUT = 3000;

	/**
	 * Check if a new Jbang version is available and if so notify the user. This
	 * code will block while doing the check.
	 */
	public static void checkNowAndInform() {
		String latestVersion = retrieveLatestVersion(false);
		if (isNewer(latestVersion)) {
			showMessage(latestVersion);
		} else {
			Util.infoMsg("jbang is up-to-date.");
		}
	}

	/**
	 * Very specialized function used to check if jbang can be updated. It returns
	 * `true` when we're running a managed jbang and either there is a newer version
	 * or `checkForUpdate` was disabled. In all other cases it will return `false`
	 * and informing the user that either jbang is already up-to-date or telling
	 * them how to update jbang themselves (if `checkForUpdate` was disabled).
	 * 
	 * @param checkForUpdate do we check for a new version or not?
	 * @return do we update jbang or not?
	 */
	public static boolean updateOrInform(boolean checkForUpdate) {
		if (Util.runningManagedJbang()) {
			if (!checkForUpdate || isNewer(retrieveLatestVersion(false))) {
				return true;
			} else if (checkForUpdate) {
				Util.infoMsg("jbang is up-to-date.");
			}
		} else {
			if (checkForUpdate) {
				checkNowAndInform();
			} else {
				showManualInstallMessage();
			}
		}
		return false;
	}

	/**
	 * Asynchronously retrieve the latest Jbang version number but only if enough
	 * time has passed since the last time we checked. The result of this function
	 * can be used together with `showMessage()` to inform the user.
	 * 
	 * @return A future with the latest version number or `null` if not enough time
	 *         has passed since the last check.
	 */
	public static CompletableFuture<String> newerVersionAsync() {
		return CompletableFuture.supplyAsync(VersionChecker::newerVersion);
	}

	/**
	 * Inform the user if the future returned a newer jbang version number
	 */
	public static void inform(CompletableFuture<String> versionCheckResult) {
		versionCheckResult.thenAccept(latestVersion -> inform(latestVersion));
	}

	private static void inform(String latestVersion) {
		if (latestVersion != null) {
			if (isNewer(latestVersion)) {
				showMessage(latestVersion);
			} else {
				Util.verboseMsg("jbang is up-to-date.");
			}
		}
	}

	private static void showMessage(String latestVersion) {
		Util.infoMsg("There is a new version of jbang available!");
		Util.infoMsg("You have version " + Util.getJbangVersion()
				+ " and " + latestVersion + " is the latest.");
		if (Util.runningManagedJbang()) {
			Util.infoMsg("Run 'jbang app install --force jbang' to update to the latest version.");
		} else {
			showManualInstallMessage();
		}
	}

	private static void showManualInstallMessage() {
		Util.infoMsg("Use your package manager to update jbang to the latest version,");
		Util.infoMsg("or visit https://jbang.dev to download and install it yourself.");
	}

	/**
	 * First we determine when was the last time we checked Jbang's latest version
	 * and if enough time has passed (so as not to annoy the user with too frequent
	 * messages) we perform the check and return the new version number, otherwise
	 * we return `null`. NB: The check is never performed if Quiet mode is enabled.
	 */
	public static String newerVersion() {
		if (shouldCheck()) {
			return retrieveLatestVersion(true);
		}
		return null;
	}

	// Figure out if we should check now or wait for a couple of days
	private static boolean shouldCheck() {
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
	private static boolean isNewer(String latestVersion) {
		return (latestVersion != null && compareVersions(latestVersion, Util.getJbangVersion()) > 0);
	}

	// Determines and returns the latest Jbang version from GitHub
	private static String retrieveLatestVersion(boolean suppressError) {
		try {
			Path versionFile = Util.downloadFile(jbangVersionUrl, Settings.getCacheDir().toFile(), CONNECT_TIMEOUT);
			List<String> lines = Files.readAllLines(versionFile);
			if (!lines.isEmpty()) {
				return lines.get(0);
			}
		} catch (IOException e) {
			if (suppressError) {
				Util.verboseMsg("Couldn't determine latest Jbang version", e);
			} else {
				throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Could not retrieve latest jbang version", e);
			}
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

}
