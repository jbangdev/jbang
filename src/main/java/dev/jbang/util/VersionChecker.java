package dev.jbang.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;

public class VersionChecker {
	private static final String jbangVersionUrl = "https://www.jbang.dev/releases/latest/download/version.txt";

	private static final long DELAY_DAYS = 1;
	private static final int CONNECT_TIMEOUT = 3000;

	private static Future<String> versionCheckResult;
	private static boolean informed = false;

	/**
	 * Check if a new JBang version is available and if so notify the user. This
	 * code will block while doing the check.
	 */
	public static void checkNowAndInform() {
		try {
			inform(retrieveLatestVersionAsync().get(), false);
		} catch (ExecutionException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Couldn't retrieve latest jbang version");
		} catch (InterruptedException e) {
			// Ignore
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
		try {
			if (Util.runningManagedJBang()) {
				String latestVersion = retrieveLatestVersionAsync().get();
				if (!checkForUpdate || isNewer(latestVersion)) {
					return true;
				} else if (checkForUpdate) {
					inform(latestVersion, false);
				}
			} else {
				if (checkForUpdate) {
					checkNowAndInform();
				} else if (!informed) {
					showManualInstallMessage();
				}
			}
		} catch (ExecutionException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Couldn't retrieve latest jbang version");
		} catch (InterruptedException e) {
			// Ignore
		}
		return false;
	}

	/**
	 * Asynchronously retrieve the latest JBang version number but only if enough
	 * time has passed since the last time we checked. The result of this function
	 * can be used together with `showMessage()` to inform the user.
	 * 
	 * @return A future to obtain the latest version number or `null` if not enough
	 *         time has passed since the last check.
	 */
	public static Future<String> newerVersionAsync() {
		if (shouldCheck()) {
			return retrieveLatestVersionAsync();
		}
		return null;
	}

	/**
	 * Inform the user if the future returned a newer JBang version number, or
	 * cancel the Future if it isn't done yet.
	 */
	public static void informOrCancel(Future<String> versionCheckResult) {
		try {
			if (versionCheckResult != null) {
				if (versionCheckResult.isDone()) {
					inform(versionCheckResult.get(), true);
				} else {
					versionCheckResult.cancel(true);
				}
			}
		} catch (ExecutionException e) {
			Util.verboseMsg("Couldn't retrieve latest jbang version", e);
		} catch (InterruptedException e) {
			// Ignore
		} catch (java.util.concurrent.CancellationException e) {
			// Ignore
		}
	}

	private static void inform(String latestVersion, boolean quiet) {
		if (!informed && latestVersion != null) {
			if (isNewer(latestVersion)) {
				showMessage(latestVersion);
			} else {
				if (quiet) {
					Util.verboseMsg("jbang is up-to-date.");
				} else {
					Util.infoMsg("jbang is up-to-date.");
				}
			}
			informed = true;
		}
	}

	private static void showMessage(String latestVersion) {
		Util.infoMsg("There is a new version of jbang available!");
		Util.infoMsg("You have version " + Util.getJBangVersion()
				+ " and " + latestVersion + " is the latest.");
		if (Util.runningManagedJBang()) {
			Util.infoMsg("Run 'jbang version --update' to update to the latest version.");
		} else {
			showManualInstallMessage();
		}
	}

	private static void showManualInstallMessage() {
		Util.infoMsg("Use your package manager to update jbang to the latest version,");
		Util.infoMsg("or visit https://jbang.dev to download and install it yourself.");
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
			} else {
				Files.createFile(versionFile);
				return false;
			}
		} catch (IOException e) {
			// ignore
		}
		return true;
	}

	/**
	 * Checks if we're running the latest version of JBang by comparing its version
	 * against the latest version on GitHub.
	 */
	private static boolean isNewer(String latestVersion) {
		return (latestVersion != null && compareVersions(latestVersion, Util.getJBangVersion()) > 0);
	}

	private static Future<String> retrieveLatestVersionAsync() {
		if (versionCheckResult == null) {
			versionCheckResult = Executors.newSingleThreadExecutor().submit(VersionChecker::retrieveLatestVersion);
		}
		return versionCheckResult;
	}

	// Determines and returns the latest JBang version from GitHub
	private static String retrieveLatestVersion() throws IOException {
		Path versionFile = NetUtil.downloadFile(jbangVersionUrl, Settings.getCacheDir(), CONNECT_TIMEOUT);
		List<String> lines = Files.readAllLines(versionFile);
		if (!lines.isEmpty()) {
			return lines.get(0);
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
