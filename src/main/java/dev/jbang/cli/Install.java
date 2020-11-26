package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.UnpackUtil;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "install", description = "Makes a script available on the user's PATH.")
public class Install extends BaseCommand {
	private static final String jbangUrl = "https://github.com/jbangdev/jbang/releases/latest/download/jbang.zip";

	@CommandLine.Option(names = {
			"--force" }, description = "Force re-installation")
	boolean force;

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "A name for the command", arity = "1")
	String name;

	@CommandLine.Parameters(paramLabel = "scriptRef", index = "1", description = "A file or URL to a Java code file or an alias", arity = "0..1")
	String scriptRef;

	@Override
	public Integer doCall() throws IOException {
		boolean installed = false;
		if (name.equals("jbang")) {
			if (scriptRef != null && !scriptRef.isEmpty()) {
				throw new IllegalArgumentException(
						"jbang is a reserved name. If you're looking to install jbang itself remove the last argument and re-run");
			}
			installed = installJbang(force);
		} else {
			if (scriptRef == null || scriptRef.isEmpty()) {
				throw new IllegalArgumentException("Missing required parameter: 'scriptRef'");
			}
			installed = install(name, scriptRef, force);
		}
		if (installed) {
			if (Setup.needsSetup()) {
				return Setup.setup(Setup.guessWithJava(), false);
			}
		}
		return EXIT_OK;
	}

	public static boolean install(String name, String scriptRef, boolean force) throws IOException {
		throw new ExitException(EXIT_GENERIC_ERROR, "Installation of scripts is not implemented yet.");
	}

	public static boolean installJbang(boolean force) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		boolean managedJbang = Files.exists(binDir.resolve("jbang.jar"));

		if (!force && (managedJbang || searchPath("jbang") != null)) {
			Util.infoMsg("jbang is already available, re-run with --force to install anyway.");
			return false;
		}

		if (force || !managedJbang) {
			// Download Jbang and unzip to ~/.jbang/bin/
			Util.infoMsg("Downloading and installing jbang...");
			Path zipFile = Util.downloadAndCacheFile(jbangUrl, force);
			Path urlsDir = Settings.getCacheDir(Settings.CacheClass.urls);
			Util.deletePath(urlsDir.resolve("jbang"), true);
			UnpackUtil.unpack(zipFile, urlsDir);
			deleteJbangFiles(binDir);
			Path fromDir = urlsDir.resolve("jbang").resolve("bin");
			copyJbangFiles(fromDir, binDir);
		} else {
			Util.infoMsg("jbang is already installed.");
		}
		return true;
	}

	private static void deleteJbangFiles(Path dir) {
		try {
			Files	.list(dir)
					.filter(f -> f.toString().equals("jbang") || f.toString().startsWith("jbang."))
					.forEach(f -> Util.deletePath(f, true));
		} catch (IOException e) {
			// Ignore
		}
	}

	private static void copyJbangFiles(Path from, Path to) throws IOException {
		to.toFile().mkdirs();
		Files	.list(from)
				.map(from::relativize)
				.forEach(f -> {
					try {
						Files.copy(from.resolve(f), to.resolve(f), StandardCopyOption.REPLACE_EXISTING,
								StandardCopyOption.COPY_ATTRIBUTES);
					} catch (IOException e) {
						throw new ExitException(-1, "Could not copy " + f.toString(), e);
					}
				});
	}

	private static Path searchPath(String name) {
		String envPath = System.getenv("PATH");
		envPath = envPath != null ? envPath : "";
		return Arrays	.stream(envPath.split(File.pathSeparator))
						.map(p -> Paths.get(p))
						.filter(p -> isExecutable(p.resolve(name)))
						.findFirst()
						.get();
	}

	private static boolean isExecutable(Path file) {
		if (Files.isExecutable(file)) {
			if (Util.isWindows()) {
				String nm = file.getFileName().toString().toLowerCase();
				if (nm.endsWith(".exe") || nm.endsWith(".bat") || nm.endsWith(".cmd") || nm.endsWith(".ps1")) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}
}
