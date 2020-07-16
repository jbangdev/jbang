package dev.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JdkManager {
	private static final String JDK_DOWNLOAD_URL = "https://api.adoptopenjdk.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/adoptopenjdk";

	public static Path getCurrentJdk(String requestedVersion) throws IOException {
		int currentVersion = JavaUtil.determineJavaVersion();
		int actualVersion = JavaUtil.javaVersion(requestedVersion);
		if (currentVersion == actualVersion) {
			return JavaUtil.getJdkHome();
		} else {
			return getInstalledJdk(actualVersion);
		}
	}

	public static Path getInstalledJdk(int version) throws IOException {
		Path jdkDir = getJdkPath(version);
		if (!Files.isDirectory(jdkDir)) {
			jdkDir = downloadAndInstallJdk(version, false);
		}
		return jdkDir;
	}

	public static Path downloadAndInstallJdk(int version, boolean updateCache) throws IOException {
		System.err.println("Downloading JDK " + version + "...");
		String url = String.format(JDK_DOWNLOAD_URL, version, Util.getOS().name(), Util.getArch().name());
		Path jdkPkg = Util.downloadAndCacheFile(url, updateCache);
		Path jdkDir = getJdkPath(version);
		try {
			System.err.println("Installing JDK " + version + "...");
			UnpackUtil.unpack(jdkPkg, jdkDir);
		} catch (Throwable th) {
			// Make sure we don't leave halfway unpacked files around
			Util.deleteFolder(jdkDir, true);
		}
		return jdkDir;
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
