package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

	private static final String FOOJAY_JDK_DOWNLOAD_URL = "https://api.foojay.io/disco/v2.0/directuris?";

	static Map<String, String> parameters(int version, Util.Vendor distro, Util.Release release, String arch,
			String archiveType, String os) {

		Map<String, String> param = new HashMap<>();
		param.put("version", String.valueOf(version));

		if (distro == null) {
			if (version == 8 || version == 11 || version >= 17) {
				distro = Util.Vendor.temurin;
			} else {
				distro = Util.Vendor.adoptopenjdk;
			}
		}
		param.put("distro", distro.foojayname());

		if (archiveType == null) {
			if ("windows".equals(os)) {
				archiveType = "zip";
			} else {
				archiveType = "tar.gz";
			}
		}

		param.put("archive_type", archiveType);

		param.put("architecture", arch);
		param.put("package_type", "jdk");
		param.put("operating_system", os);

		if ("windows".equals(os)) {
			param.put("libc_type", "c_std_lib");
		} else if (os.equals("mac")) {
			param.put("libc_type", "libc");
		} else {
			param.put("libc_type", "glibc");
		}

		if (release == null) {
			release = Util.Release.ga;
		}
		param.put("release_status", release.name());

		param.put("javafx_bundled", "false");
		param.put("latest", "available");

		return param;
	}

	private static String getJDKUrl(int version, String os, String architecture, Util.Vendor vendor,
			Util.Release release) {
		String url = FOOJAY_JDK_DOWNLOAD_URL
				+ urlEncodeUTF8(parameters(version, vendor, release, architecture, null, os));
		return url;
	}

	static String urlEncodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	static String urlEncodeUTF8(Map<?, ?> map) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append(String.format("%s=%s",
					urlEncodeUTF8(entry.getKey().toString()),
					urlEncodeUTF8(entry.getValue().toString())));
		}
		return sb.toString();
	}

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
		String url = getJDKUrl(version, Util.getOS().name(), Util.getArch().name(),
				Util.getVendor(), Util.getRelease());
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

	/**
	 * Links JBang JDK folder to an already existing JDK path with a link. It checks
	 * if the incoming version number is the same that the linked JDK has, if not an
	 * exception will be raised.
	 *
	 * @param path    path to the pre-installed JDK.
	 * @param version requested version to link.
	 */
	public static void linkToExistingJdk(String path, int version) {
		Path jdkPath = getJdkPath(version);
		Util.verboseMsg("Trying to link " + path + " to " + jdkPath);
		if (Files.exists(jdkPath)) {
			Util.verboseMsg("JBang managed JDK already exists, must be deleted to make sure linking works");
			Util.deletePath(jdkPath, false);
		}
		Path linkedJdkPath = Paths.get(path);
		if (!Files.isDirectory(linkedJdkPath)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to resolve path as directory: " + path);
		}
		Optional<Integer> ver = resolveJavaVersionFromPath(linkedJdkPath);
		if (ver.isPresent()) {
			Integer linkedJdkVersion = ver.get();
			if (linkedJdkVersion == version) {
				Util.createLink(jdkPath, linkedJdkPath);
				Util.infoMsg("JDK " + version + " has been linked to: " + linkedJdkPath);
			} else {
				throw new ExitException(EXIT_INVALID_INPUT, "Java version in given path: " + path
						+ " is " + linkedJdkVersion + " which does not match the requested version " + version + "");
			}
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to determine Java version in given path: " + path);
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
					Optional<Integer> ver = resolveJavaVersionFromPath(link);
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

	public static Optional<Integer> resolveJavaVersionFromPath(Path link) {
		try {
			return Files.lines(link.resolve("release"))
						.filter(l -> l.startsWith("JAVA_VERSION"))
						.map(JavaUtil::parseJavaOutput)
						.findAny();
		} catch (IOException e) {
			Util.verboseMsg("Unable to read 'release' file in path:" + link);
			return Optional.empty();
		}
	}
}
