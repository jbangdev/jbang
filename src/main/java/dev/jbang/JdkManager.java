package dev.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JdkManager {
	private static final String JDK_DOWNLOAD_URL = "https://api.adoptopenjdk.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/adoptopenjdk";

	private static final int DEFAULT_JAVA_VERSION = 11;

	public static Path getCurrentJdk(Integer expectedVersion) throws IOException {
		int currentVersion = JavaUtil.determineJavaVersion();
		if (expectedVersion != null) {
			if (currentVersion < expectedVersion) {
				return getJdk(expectedVersion);
			} else {
				return JavaUtil.getJdkHome();
			}
		} else {
			if (currentVersion < 8) {
				return getJdk(DEFAULT_JAVA_VERSION);
			} else {
				return JavaUtil.getJdkHome();
			}
		}
	}

	public static Path getJdk(int version) throws IOException {
		Path jdkDir = getJdkPath(version);
		if (!Files.isDirectory(jdkDir)) {
			jdkDir = downloadAndInstallJdk(version);
		}
		return jdkDir;
	}

	public static Path downloadAndInstallJdk(int version) throws IOException {
		String url = String.format(JDK_DOWNLOAD_URL, version, Util.getOS().name(), Util.getArch().name());
		Path jdkPkg = Util.downloadAndCacheFile(url, false);
		Path jdkDir = getJdkPath(version);
		try {
			UnpackUtil.unpack(jdkPkg, jdkDir);
		} catch (Throwable th) {
			// Make sure we don't leave halfway unpacked files around
			Util.deleteFolder(jdkDir, true);
		}
		return jdkDir;
	}

	private static Path getJdkPath(int version) {
		return Settings.getCacheDir().resolve("jdks").resolve(Integer.toString(version));
	}
}
