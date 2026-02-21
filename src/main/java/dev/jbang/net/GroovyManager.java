package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.NetUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

public class GroovyManager {
	public static final String DEFAULT_GROOVY_VERSION = "4.0.30";

	public static String resolveInGroovyHome(String cmd, String requestedVersion) {
		Path groovyHome = getGroovy(requestedVersion);
		if (Util.isWindows()) {
			cmd = cmd + ".bat";
		}
		return groovyHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
	}

	public static Path getGroovy(String requestedVersion) {
		Path groovyPath = getGroovyPath(requestedVersion);
		if (!Files.isDirectory(groovyPath)) {
			groovyPath = downloadAndInstallGroovy(requestedVersion);
		}
		return groovyPath.resolve("groovy-" + requestedVersion);
	}

	public static Path downloadAndInstallGroovy(String version) {
		Util.infoMsg("Downloading Groovy " + version + ". Be patient, this can take several minutes...");
		String url = getGroovyDownloadUrl(version);
		Util.verboseMsg("Downloading " + url);
		Path groovyDir = getGroovyPath(version);
		Path groovyTmpDir = groovyDir.getParent().resolve(groovyDir.getFileName().toString() + ".tmp");
		Path groovyOldDir = groovyDir.getParent().resolve(groovyDir.getFileName().toString() + ".old");
		Util.deletePath(groovyTmpDir, false);
		Util.deletePath(groovyOldDir, false);
		try {
			Path groovyPkg = NetUtil.downloadAndCacheFile(url);
			Util.infoMsg("Installing Groovy " + version + "...");
			Util.verboseMsg("Unpacking to " + groovyDir);
			UnpackUtil.unpack(groovyPkg, groovyTmpDir);
			if (Files.isDirectory(groovyDir)) {
				Files.move(groovyDir, groovyOldDir);
			}
			Files.move(groovyTmpDir, groovyDir);
			Util.deletePath(groovyOldDir, false);
			return groovyDir;
		} catch (Exception e) {
			Util.deletePath(groovyTmpDir, true);
			if (!Files.isDirectory(groovyDir) && Files.isDirectory(groovyOldDir)) {
				try {
					Files.move(groovyOldDir, groovyDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			Util.errorMsg("Required Groovy version not possible to download or install.");
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download or install Groovy version " + version, e);
		}
	}

	public static Path getGroovyPath(String version) {
		return getGroovycsPath().resolve(version);
	}

	private static Path getGroovycsPath() {
		return Settings.getCacheDir(Cache.CacheClass.groovycs);
	}

	private static String getGroovyDownloadUrl(String version) {
		int groovyMajorVersion = 4;
		Matcher matcher = Pattern.compile("(\\d)\\..*").matcher(version);
		if (matcher.find()) {
			groovyMajorVersion = Integer.parseInt(matcher.group(1));
		}
		if (groovyMajorVersion >= 4) {
			return String.format(
					"https://repo1.maven.org/maven2/org/apache/groovy/groovy-binary/%s/groovy-binary-%s.zip", version,
					version);
		} else {
			return String.format(
					"https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-binary/%s/groovy-binary-%s.zip", version,
					version);
		}
	}

}
