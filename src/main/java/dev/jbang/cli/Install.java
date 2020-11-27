package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
			// if (scriptRef == null || scriptRef.isEmpty()) {
			// throw new IllegalArgumentException("Missing required parameter:
			// 'scriptRef'");
			// }
			if (scriptRef == null) {
				scriptRef = name;
			}
			installed = install(name, scriptRef, force);
		}
		if (installed) {
			if (Setup.needsSetup()) {
				return Setup.setup(Setup.guessWithJava(), false, false);
			}
		}
		return EXIT_OK;
	}

	public static boolean install(String name, String scriptRef, boolean force) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		if (Files.exists(binDir.resolve(name)) || Files.exists(binDir.resolve(name))
				|| Files.exists(binDir.resolve(name))) {
			Util.infoMsg(name + " is already available.");
			return false;
		}
		BaseScriptCommand.prepareScript(scriptRef);
		installScripts(name, scriptRef);
		return true;
	}

	private static void installScripts(String name, String scriptRef) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		if (Util.isWindows()) {
			installCmdScript(binDir.resolve(name + ".cmd"), scriptRef);
			installPSScript(binDir.resolve(name + ".ps1"), scriptRef);
		} else {
			installShellScript(binDir.resolve(name), scriptRef);
		}
	}

	private static void installShellScript(Path file, String scriptRef) throws IOException {
		List<String> lines = Arrays.asList(
				"#!/bin/sh",
				"eval \"exec jbang run " + scriptRef + " $*\"");
		Files.write(file, lines, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		setExecutable(file);
	}

	private static void setExecutable(Path file) {
		final Set<PosixFilePermission> permissions;
		try {
			permissions = Files.getPosixFilePermissions(file);
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
			Files.setPosixFilePermissions(file, permissions);
		} catch (UnsupportedOperationException | IOException e) {
			throw new ExitException(EXIT_GENERIC_ERROR, "Couldn't mark script as executable: " + file, e);
		}
	}

	private static void installCmdScript(Path file, String scriptRef) throws IOException {
		List<String> lines = Arrays.asList(
				"@echo off",
				"jbang run " + scriptRef + " %*");
		Files.write(file, lines, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
	}

	private static void installPSScript(Path file, String scriptRef) throws IOException {
		List<String> lines = Arrays.asList(
				"jbang run " + scriptRef + " $args");
		Files.write(file, lines, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
	}

	public static boolean installJbang(boolean force) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		boolean managedJbang = Files.exists(binDir.resolve("jbang.jar"));

		if (!force && (managedJbang || Util.searchPath("jbang") != null)) {
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
}
