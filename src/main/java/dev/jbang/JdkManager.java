package dev.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
