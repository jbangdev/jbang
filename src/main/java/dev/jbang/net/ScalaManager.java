package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.NetUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

public class ScalaManager {
	private static final String SCALA3_DOWNLOAD_URL = "https://github.com/scala/scala3/releases/download/%s/scala3-%s.zip";
	private static final String SCALA2_DOWNLOAD_URL = "https://github.com/scala/scala/releases/download/v%s/scala-%s.zip";

	public static final String DEFAULT_SCALA_VERSION = "3.8.1";
	public static final String DEFAULT_SCALA_2_VERSION = "2.13.18";

	public static String resolveInScalaHome(String cmd, String requestedVersion) {
		Path scalaHome = getScala(requestedVersion);
		if (Util.isWindows()) {
			cmd = cmd + ".bat";
		}
		return scalaHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
	}

	public static Path getScala(String requestedVersion) {
		String normalizedVersion = normalizeVersion(requestedVersion);
		Path scalaPath = getScalaPath(normalizedVersion);
		if (!Files.isDirectory(scalaPath)) {
			scalaPath = downloadAndInstallScala(normalizedVersion);
		}
		return scalaPath.resolve(getScalaDirectoryName(normalizedVersion));
	}

	public static Path downloadAndInstallScala(String requestedVersion) {
		String normalizedVersion = normalizeVersion(requestedVersion);
		Util.infoMsg("Downloading Scala " + normalizedVersion + ". Be patient, this can take several minutes...");
		String url = getScalaDownloadUrl(normalizedVersion);
		Util.verboseMsg("Downloading " + url);
		Path scalaDir = getScalaPath(normalizedVersion);
		Path scalaTmpDir = scalaDir.getParent().resolve(scalaDir.getFileName().toString() + ".tmp");
		Path scalaOldDir = scalaDir.getParent().resolve(scalaDir.getFileName().toString() + ".old");
		Util.deletePath(scalaTmpDir, false);
		Util.deletePath(scalaOldDir, false);
		try {
			Path scalaPkg = NetUtil.downloadAndCacheFile(url);
			Util.infoMsg("Installing Scala " + normalizedVersion + "...");
			Util.verboseMsg("Unpacking to " + scalaDir);
			UnpackUtil.unpack(scalaPkg, scalaTmpDir);
			if (Files.isDirectory(scalaDir)) {
				Files.move(scalaDir, scalaOldDir);
			}
			Files.move(scalaTmpDir, scalaDir);
			Util.deletePath(scalaOldDir, false);
			return scalaDir;
		} catch (Exception e) {
			Util.deletePath(scalaTmpDir, true);
			if (!Files.isDirectory(scalaDir) && Files.isDirectory(scalaOldDir)) {
				try {
					Files.move(scalaOldDir, scalaDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			Util.errorMsg("Required Scala version not possible to download or install.");
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download or install scalac version " + normalizedVersion, e);
		}
	}

	public static Path getScalaPath(String version) {
		return getScalacsPath().resolve(normalizeVersion(version));
	}

	public static String normalizeVersion(String requestedVersion) {
		if (Util.isBlankString(requestedVersion)) {
			return DEFAULT_SCALA_VERSION;
		}
		String version = requestedVersion.trim();
		if (version.startsWith("v")) {
			version = version.substring(1);
		}
		if ("3".equals(version)) {
			return DEFAULT_SCALA_VERSION;
		}
		if ("2".equals(version) || "2.13".equals(version)) {
			return DEFAULT_SCALA_2_VERSION;
		}
		return version;
	}

	private static Path getScalacsPath() {
		return Settings.getCacheDir(Cache.CacheClass.scalacs);
	}

	private static String getScalaDownloadUrl(String version) {
		if (isScala2(version)) {
			return String.format(SCALA2_DOWNLOAD_URL, version, version);
		}
		return String.format(SCALA3_DOWNLOAD_URL, version, version);
	}

	private static String getScalaDirectoryName(String version) {
		if (isScala2(version)) {
			return "scala-" + version;
		}
		return "scala3-" + version;
	}

	private static boolean isScala2(String version) {
		return version.startsWith("2.");
	}
}
