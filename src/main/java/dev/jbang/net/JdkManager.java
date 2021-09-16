package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

public class JdkManager {
	private static final String JDK_DOWNLOAD_URL = "https://api.adoptopenjdk.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/%s";

	public static Path getCurrentJdk(String requestedVersion) {
		int currentVersion = JavaUtil.determineJavaVersion();
		int actualVersion = JavaUtil.javaVersion(requestedVersion);
		if (currentVersion == actualVersion) {
			Util.verboseMsg("System Java version matches requested version " + actualVersion);
			return JavaUtil.getJdkHome();
		} else {
			if (currentVersion == 0) {
				Util.verboseMsg("No system Java found, using JBang managed version " + actualVersion);
			} else {
				Util.verboseMsg("System Java version " + currentVersion + " incompatible, using JBang managed version "
						+ actualVersion);
			}
			return getInstalledJdk(actualVersion);
		}
	}

	public static Path getInstalledJdk(int version) {
		Path jdkDir = getJdkPath(version);
		if (!Files.isDirectory(jdkDir)) {
			jdkDir = downloadAndInstallJdk(version);
		}
		return jdkDir;
	}

	public static Path downloadAndInstallJdk(int version) {
		Util.infoMsg("Downloading JDK " + version + ". Be patient, this can take several minutes...");
		String url = String.format(JDK_DOWNLOAD_URL, version, Util.getOS().name(), Util.getArch().name(),
				Util.getVendor().name());
		Util.verboseMsg("Downloading " + url);
		Path jdkDir = getJdkPath(version);
		Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName().toString() + ".tmp");
		Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName().toString() + ".old");
		Util.deletePath(jdkTmpDir, false);
		Util.deletePath(jdkOldDir, false);
		try {
			Path jdkPkg = Util.downloadAndCacheFile(url);
			Util.infoMsg("Installing JDK " + version + "...");
			Util.verboseMsg("Unpacking to " + jdkDir.toString());
			UnpackUtil.unpackJdk(jdkPkg, jdkTmpDir);
			if (Files.isDirectory(jdkDir)) {
				Files.move(jdkDir, jdkOldDir);
			}
			Files.move(jdkTmpDir, jdkDir);
			Util.deletePath(jdkOldDir, false);
			if (getDefaultJdk() < 0) {
				setDefaultJdk(version);
			}
			return jdkDir;
		} catch (Exception e) {
			Util.deletePath(jdkTmpDir, true);
			if (!Files.isDirectory(jdkDir) && Files.isDirectory(jdkOldDir)) {
				try {
					Files.move(jdkOldDir, jdkDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			Util.errorMsg("Required Java version not possible to download or install. You can run with '--java "
					+ JavaUtil.determineJavaVersion() + "' to force using the default installed Java.");
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download or install JDK version " + version, e);
		}
	}

	public static void uninstallJdk(int version) {
		Path jdkDir = JdkManager.getInstalledJdk(version);
		if (jdkDir != null) {
			int defaultJdk = getDefaultJdk();
			if (Util.isWindows()) {
				// On Windows we have to check nobody is currently using the JDK or we could
				// be causing all kinds of trouble
				try {
					Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName().toString() + ".tmp");
					Util.deletePath(jdkTmpDir, true);
					Files.move(jdkDir, jdkTmpDir);
					jdkDir = jdkTmpDir;
				} catch (IOException ex) {
					Util.warnMsg("Cannot uninstall JDK " + version + ", it's being used");
					return;
				}
			}
			Util.deletePath(jdkDir, false);
			if (defaultJdk == version) {
				Optional<Integer> newver = nextInstalledJdk(version);
				if (!newver.isPresent()) {
					newver = prevInstalledJdk(version);
				}
				if (newver.isPresent()) {
					setDefaultJdk(newver.get());
				} else {
					removeDefaultJdk();
					Util.infoMsg("Default JDK unset");
				}
			}
		}
	}

	public static Optional<Integer> nextInstalledJdk(int minVersion) {
		return listInstalledJdks()
									.stream()
									.filter(v -> v >= minVersion)
									.min(Integer::compareTo);
	}

	public static Optional<Integer> prevInstalledJdk(int maxVersion) {
		return listInstalledJdks()
									.stream()
									.filter(v -> v <= maxVersion)
									.max(Integer::compareTo);
	}

	public static Set<Integer> listInstalledJdks() {
		if (Files.isDirectory(getJdksPath())) {
			Supplier<TreeSet<Integer>> sset = TreeSet::new;
			try (Stream<Path> files = Files.list(getJdksPath())) {
				return files.map(p -> {
					try {
						return Integer.parseInt(p.getFileName().toString());
					} catch (NumberFormatException ex) {
						return -1;
					}
				}).filter(v -> v > 0).collect(Collectors.toCollection(sset));
			} catch (IOException e) {
				Util.verboseMsg("Couldn't list installed JDKs", e);
			}
		}
		return Collections.emptySet();
	}

	public static boolean isInstalledJdk(int version) {
		return Files.isDirectory(getJdkPath(version));
	}

	public static void setDefaultJdk(int version) {
		if (!isInstalledJdk(version) || getDefaultJdk() != version) {
			Path jdk = getInstalledJdk(version);
			// Check again if we really need to create a link because the
			// previous line might already have caused it to be created
			if (getDefaultJdk() != version) {
				removeDefaultJdk();
				Util.createLink(Settings.getCurrentJdkDir(), jdk);
				Util.infoMsg("Default JDK set to " + version);
			}
		}
	}

	public static int getDefaultJdk() {
		try {
			Path link = Settings.getCurrentJdkDir();
			if (Files.isDirectory(link)) {
				if (Files.isSymbolicLink(link)) {
					Path dest = Files.readSymbolicLink(link);
					return Integer.parseInt(dest.getFileName().toString());
				} else {
					// Should be a hard link, so we can't parse the version number
					// from the directory name, so we read the "release" file instead.
					Optional<Integer> ver = Files	.lines(link.resolve("release"))
													.filter(l -> l.startsWith("JAVA_VERSION"))
													.map(JavaUtil::parseJavaOutput)
													.findAny();
					if (ver.isPresent()) {
						return ver.get();
					}
				}
			}
		} catch (IOException ex) {
			// Ignore
		}
		return -1;
	}

	public static void removeDefaultJdk() {
		Path link = Settings.getCurrentJdkDir();
		if (Files.isSymbolicLink(link)) {
			try {
				Files.deleteIfExists(link);
			} catch (IOException e) {
				// Ignore
			}
		} else {
			Util.deletePath(link, true);
		}
	}

	public static boolean isCurrentJdkManaged() {
		Path home = JavaUtil.getJdkHome();
		return (home != null && home.startsWith(getJdksPath()));
	}

	public static Path getJdkPath(int version) {
		return getJdksPath().resolve(Integer.toString(version));
	}

	private static Path getJdksPath() {
		return Settings.getCacheDir(Cache.CacheClass.jdks);
	}
}
