package dev.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import picocli.CommandLine;

public class JdkManager {
	private static final String JDK_DOWNLOAD_URL = "https://api.adoptopenjdk.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/adoptopenjdk";

	public static Path getCurrentJdk(String requestedVersion) {
		int currentVersion = JavaUtil.determineJavaVersion();
		int actualVersion = JavaUtil.javaVersion(requestedVersion);
		if (currentVersion == actualVersion) {
			return JavaUtil.getJdkHome();
		} else {
			return getInstalledJdk(actualVersion);
		}
	}

	public static Path getInstalledJdk(int version) {
		Path jdkDir = getJdkPath(version);
		if (!Files.isDirectory(jdkDir)) {
			jdkDir = downloadAndInstallJdk(version, false);
		}
		return jdkDir;
	}

	public static Path downloadAndInstallJdk(int version, boolean updateCache) {
		Util.infoMsg("Downloading JDK " + version + "...");
		String url = String.format(JDK_DOWNLOAD_URL, version, Util.getOS().name(), Util.getArch().name());
		Path jdkDir = getJdkPath(version);
		Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName().toString() + ".tmp");
		Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName().toString() + ".old");
		Util.deleteFolder(jdkTmpDir, false);
		Util.deleteFolder(jdkOldDir, false);
		try {
			Path jdkPkg = Util.downloadAndCacheFile(url, updateCache);
			Util.infoMsg("Installing JDK " + version + "...");
			UnpackUtil.unpack(jdkPkg, jdkTmpDir);
			if (Files.isDirectory(jdkDir)) {
				Files.move(jdkDir, jdkOldDir);
			}
			Files.move(jdkTmpDir, jdkDir);
			Util.deleteFolder(jdkOldDir, false);
			return jdkDir;
		} catch (Exception e) {
			Util.deleteFolder(jdkTmpDir, true);
			Util.errorMsg("Required Java version not possible to download or install. You can run with '--java "
					+ JavaUtil.determineJavaVersion() + "' to force using the default installed Java.");
			throw new ExitException(CommandLine.ExitCode.SOFTWARE,
					"Unable to download or install JDK version " + version, e);
		}
	}

	public static Set<Integer> listInstalledJdks() throws IOException {
		if (Files.isDirectory(getJdksPath())) {
			Supplier<TreeSet<Integer>> sset = () -> new TreeSet<>();
			return Files.list(getJdksPath()).map(p -> {
				try {
					return Integer.parseInt(p.getFileName().toString());
				} catch (NumberFormatException ex) {
					return -1;
				}
			}).filter(v -> v > 0).collect(Collectors.toCollection(sset));
		} else {
			return Collections.emptySet();
		}
	}

	public static boolean isInstalledJdk(int version) {
		return Files.isDirectory(getJdkPath(version));
	}

	private static Path getJdkPath(int version) {
		return getJdksPath().resolve(Integer.toString(version));
	}

	private static Path getJdksPath() {
		return Settings.getCacheDir().resolve("jdks");
	}
}
