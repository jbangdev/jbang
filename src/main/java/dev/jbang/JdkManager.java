package dev.jbang;

import static java.lang.System.getenv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class JdkManager {
	private static final String JDK_DOWNLOAD_URL = "https://api.adoptopenjdk.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/adoptopenjdk";

	private static final int DEFAULT_JAVA_VERSION = 11;

	public static Path getCurrentJdk(Integer expectedVersion) throws IOException {
		Integer currentVersion = determineJavaVersion();
		if (expectedVersion != null) {
			if (currentVersion == null || currentVersion < expectedVersion) {
				return getJdk(expectedVersion);
			} else {
				return getJdkHome();
			}
		} else {
			if (currentVersion == null || currentVersion < 8) {
				return getJdk(DEFAULT_JAVA_VERSION);
			} else {
				return getJdkHome();
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

	private static Integer determineJavaVersion() {
		try {
			Path jdkHome = getJdkHome();
			String javaCmd;
			if (jdkHome != null) {
				javaCmd = jdkHome.resolve("bin").resolve("javac").toAbsolutePath().toString();
			} else {
				javaCmd = "javac";
			}
			ProcessBuilder pb = new ProcessBuilder(javaCmd, "-version");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String cmdOutput = br.lines().collect(Collectors.joining());
			int exitCode = p.waitFor();
			if (exitCode == 0) {
				String[] parts = cmdOutput.split(" ");
				if (parts.length == 2) {
					String[] nums = parts[1].split("\\.");
					String num = nums.length > 1 && nums[0].equals("1") ? nums[1] : nums[0];
					return Integer.parseInt(num);
				}
			}
		} catch (IOException | InterruptedException ex) {
			// Ignore
		}
		return null;
	}

	private static Path getJdkHome() {
		if (getenv("JAVA_HOME") != null) {
			return Paths.get(getenv("JAVA_HOME"));
		} else {
			return null;
		}
	}
}
