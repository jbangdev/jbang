package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.NetUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

public class EditorManager {
	private static final String CODIUM_DOWNLOAD_URL = "https://github.com/VSCodium/vscodium/releases/download/%s/VSCodium-%s-%s-%s.%s";

	public static Path getVSCodiumPath() {
		if (Util.isMac()) {
			return Settings.getConfigEditorDir().resolve("vscodium.app");
		} else if (Util.isWindows()) {
			return Settings.getConfigEditorDir().resolve("vscodium");
		} else {
			return Settings.getConfigEditorDir().resolve("vscodium");
		}
	}

	public static Path downloadAndInstallEditor() {
		// https://github.com/VSCodium/vscodium/releases/download/1.52.1/VSCodium-darwin-x64-1.52.1.zip

		String version = getLatestVSCodiumVersion();

		Util.infoMsg("Downloading VSCodium " + version
				+ ". Be patient, this can take several minutes...(Ctrl+C if you want to cancel)");
		String url = getVSCodiumDownloadURL(version);
		Util.verboseMsg("Downloading " + url);
		Path editorDir = getVSCodiumPath();
		Path editorTmpDir = editorDir.getParent().resolve(editorDir.getFileName().toString() + ".tmp");
		Path editorOldDir = editorDir.getParent().resolve(editorDir.getFileName().toString() + ".old");
		Util.deletePath(editorTmpDir, false);
		Util.deletePath(editorOldDir, false);
		try {
			Path jdkPkg = NetUtil.downloadAndCacheFile(url);
			Util.infoMsg("Installing VSCodium " + version + "...");
			Util.verboseMsg("Unpacking to " + editorDir.toString());
			UnpackUtil.unpackEditor(jdkPkg, editorTmpDir);
			if (Files.isDirectory(editorDir)) {
				Files.move(editorDir, editorOldDir);
			}
			Files.move(editorTmpDir, editorDir);
			Util.deletePath(editorOldDir, false);
			return editorDir;
		} catch (Exception e) {
			Util.deletePath(editorTmpDir, true);
			if (!Files.isDirectory(editorDir) && Files.isDirectory(editorOldDir)) {
				try {
					Files.move(editorOldDir, editorDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			Util.errorMsg("VSCode editor not possible to download or install.");
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Unable to download or install VSCodium version " + version,
					e);
		}

	}

	private static String getVSCodiumDownloadURL(String version) {
		String os = Util.getOS().name();
		String arch = Util.getArch().name();

		if (Util.isMac()) {
			os = "darwin";
			if ("aarch64".equals(arch)) {
				arch = "arm64";
			}
		} else if (Util.isWindows()) {
			os = "win32";
		}

		String suffix = "tar.gz";
		if (Util.isWindows() || Util.isMac()) {
			suffix = "zip";
		}
		String url = String.format(CODIUM_DOWNLOAD_URL, version, os, arch, version, suffix);
		return url;
	}

	private static String getLatestVSCodiumVersion() {
		String version = "1.52.1";
		try {
			Util.verboseMsg("Lookup vscodium latest version...");
			try (InputStream is = new URL("https://api.github.com/repos/vscodium/vscodium/releases/latest")
				.openStream();
					Scanner sc = new Scanner(is, "UTF-8")) {
				String out = sc.useDelimiter("\\A").next();
				Matcher matcher = Pattern.compile("\"tag_name\":.*?\"(.*?)\"").matcher(out);
				if (matcher.find()) {
					version = matcher.group(1);
				} else {
					Util.verboseMsg("Could not find latest version - falling back to " + version);
				}
			}

		} catch (IOException e) {
			// ignore e.printStackTrace();
		}
		return version;
	}

	public static Path getVSCodiumDataPath() {
		if (Util.isMac()) {
			return getVSCodiumPath().getParent().resolve("codium-portable-data");
		} else {
			return getVSCodiumPath().resolve("data");
		}
	}

	public static Path getVSCodiumBinPath() {
		if (Util.isMac()) {
			return getVSCodiumPath().resolve("Contents/Resources/app/bin/codium");
		} else if (Util.isWindows()) {
			return getVSCodiumPath().resolve("bin/codium.cmd");
		} else {
			return getVSCodiumPath().resolve("bin/codium");
		}
	}

	/*
	 * private static Path getJdksPath() { return
	 * Settings.getCacheDir(Settings.CacheClass.jdks); }
	 */
}
