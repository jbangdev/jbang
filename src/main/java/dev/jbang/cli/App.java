package dev.jbang.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.ExitException;
import dev.jbang.RunContext;
import dev.jbang.Settings;
import dev.jbang.Source;
import dev.jbang.Util;
import dev.jbang.net.JdkManager;
import dev.jbang.util.UnpackUtil;

import picocli.CommandLine;

@CommandLine.Command(name = "app", description = "Manage scripts installed on the user's PATH as commands.", subcommands = {
		AppInstall.class, AppList.class,
		AppUninstall.class, AppSetup.class })
public class App {

	public static void deleteCommandFiles(String name) {
		try {
			Files	.list(Settings.getConfigBinDir())
					.filter(f -> f.getFileName().toString().equals(name)
							|| f.getFileName().toString().startsWith(name + "."))
					.forEach(f -> Util.deletePath(f, true));
		} catch (IOException e) {
			// Ignore
		}
	}
}

@CommandLine.Command(name = "install", description = "Install a script as a command.")
class AppInstall extends BaseCommand {
	private static final String jbangUrl = "https://github.com/jbangdev/jbang/releases/latest/download/jbang.zip";

	@CommandLine.Option(names = {
			"--native" }, description = "Enable native build/run")
	boolean benative;

	@CommandLine.Option(names = {
			"--force" }, description = "Force re-installation")
	boolean force;

	@CommandLine.Option(names = { "--name" }, description = "A name for the command")
	String name;

	@CommandLine.Parameters(paramLabel = "scriptRef", description = "A file or URL to a Java code file or an alias")
	String scriptRef;

	@Override
	public Integer doCall() {
		boolean installed = false;
		try {
			if (scriptRef.equals("jbang")) {
				if (name != null && !"jbang".equals(name)) {
					throw new IllegalArgumentException(
							"It's not possible to install jbang with a different name");
				}
				installed = installJbang(force);
			} else {
				if ("jbang".equals(name)) {
					throw new IllegalArgumentException("jbang is a reserved name.");
				}
				if (name != null && !isValidCommandName(name)) {
					throw new IllegalArgumentException("Not a valid command name: '" + name + "'");
				}
				installed = install(name, scriptRef, force, benative);
			}
			if (installed) {
				if (AppSetup.needsSetup()) {
					return AppSetup.setup(AppSetup.guessWithJava(), false, false);
				}
			}
		} catch (IOException e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Could not install command", e);
		}
		return EXIT_OK;
	}

	public static boolean install(String name, String scriptRef, boolean force, boolean benative) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		if (!force && name != null && existScripts(binDir, name)) {
			Util.infoMsg("A script with name '" + name + "' already exists, use '--force' to install anyway.");
			return false;
		}
		RunContext ctx = RunContext.empty();
		Source src = Source.forResource(scriptRef, ctx);
		if (name == null) {
			name = chooseCommandName(ctx);
			if (!force && existScripts(binDir, name)) {
				Util.infoMsg("A script with name '" + name + "' already exists, use '--force' to install anyway.");
				return false;
			}
		}
		if (ctx.getAlias() == null && !src.getResourceRef().isURL()) {
			scriptRef = src.getResourceRef().getFile().getAbsolutePath();
		}
		installScripts(name, scriptRef, benative);
		Util.infoMsg("Command installed: " + name);
		return true;
	}

	private static boolean existScripts(Path binDir, String name) {
		return Files.exists(binDir.resolve(name)) || Files.exists(binDir.resolve(name + ".cmd"))
				|| Files.exists(binDir.resolve(name + ".ps1"));
	}

	public static String chooseCommandName(RunContext ctx) {
		String startName = null;
		String name;
		if (ctx.getAlias() != null) {
			// If the script ref is an alias we take that name up to
			// the @-symbol (if any) to be the command name.
			startName = ctx.getOriginalRef();
			name = startName;
			int p = name.indexOf("@");
			if (p > 0) {
				name = name.substring(0, p);
			}
		} else {
			// If the script is a file or a URL we take the last part of
			// the name without extension (if any) to be the command name.
			try {
				URI u = new URI(ctx.getOriginalRef());
				startName = u.getPath();
				if (startName.endsWith("/")) { // if using default app use the last segment.
					startName = startName.substring(0, startName.length() - 1);
				}
				startName = u.getPath().substring(Math.max(0, startName.lastIndexOf("/")));
			} catch (URISyntaxException e) {
				startName = Paths.get(ctx.getOriginalRef()).getFileName().toString();
			}

			name = startName;
			int p = name.lastIndexOf(".");
			if (p > 0) {
				name = name.substring(0, p);
			}
			name = name.replaceAll("[^" + validCommandNameChars + "]", "");

		}
		if (!isValidCommandName(name)) {
			throw new IllegalArgumentException(
					"A valid command name could not be determined from: '" + startName + "'");
		}
		return name;
	}

	private static final String validCommandNameChars = "-.\\w";

	private static final Pattern validCommandName = Pattern.compile("[" + validCommandNameChars + "]+");

	public static boolean isValidCommandName(String name) {
		return validCommandName.matcher(name).matches();
	}

	private static void installScripts(String name, String scriptRef, boolean benative) throws IOException {
		Path binDir = Settings.getConfigBinDir();
		binDir.toFile().mkdirs();
		if (Util.isWindows()) {
			installCmdScript(binDir.resolve(name + ".cmd"), scriptRef, benative);
			installPSScript(binDir.resolve(name + ".ps1"), scriptRef, benative);
		} else {
			installShellScript(binDir.resolve(name), scriptRef, benative);
		}
	}

	private static void installShellScript(Path file, String scriptRef, boolean benative) throws IOException {
		List<String> lines = Arrays.asList(
				"#!/bin/sh",
				"eval \"exec jbang run" + (benative ? " --native " : " ") + scriptRef + " $*\"");
		Files.write(file, lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
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

	private static void installCmdScript(Path file, String scriptRef, boolean benative) throws IOException {
		List<String> lines = Arrays.asList(
				"@echo off",
				"jbang run" + (benative ? " --native " : " ") + scriptRef + " %*");
		Files.write(file, lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
	}

	private static void installPSScript(Path file, String scriptRef, boolean benative) throws IOException {
		List<String> lines = Collections.singletonList(
				"jbang run" + (benative ? " --native " : " ") + scriptRef + " $args");
		Files.write(file, lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
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
			Path urlsDir = Settings.getCacheDir(Cache.CacheClass.urls);
			Util.deletePath(urlsDir.resolve("jbang"), true);
			UnpackUtil.unpack(zipFile, urlsDir);
			App.deleteCommandFiles("jbang");
			Path fromDir = urlsDir.resolve("jbang").resolve("bin");
			copyJbangFiles(fromDir, binDir);
		} else {
			Util.infoMsg("jbang is already installed.");
		}
		return true;
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
						throw new ExitException(EXIT_GENERIC_ERROR, "Could not copy " + f.toString(), e);
					}
				});
	}
}

@CommandLine.Command(name = "list", description = "Lists installed commands.")
class AppList extends BaseCommand {

	@Override
	public Integer doCall() {
		listCommandFiles().forEach(System.out::println);
		return EXIT_OK;
	}

	private static List<String> listCommandFiles() {
		try {
			return Files.list(Settings.getConfigBinDir())
						.map(AppList::baseFileName)
						.distinct()
						.sorted()
						.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	private static String baseFileName(Path file) {
		String nm = file.getFileName().toString();
		int p = nm.lastIndexOf('.');
		if (p > 0) {
			nm = nm.substring(0, p);
		}
		return nm;
	}
}

@CommandLine.Command(name = "uninstall", description = "Removes a previously installed command.")
class AppUninstall extends BaseCommand {

	@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the command", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		if (commandFilesExist(name)) {
			App.deleteCommandFiles(name);
			Util.infoMsg("Command removed: " + name);
			return EXIT_OK;
		} else {
			Util.infoMsg("Command not found: " + name);
			return EXIT_INVALID_INPUT;
		}
	}

	private static boolean commandFilesExist(String name) {
		try {
			return Files.list(Settings.getConfigBinDir())
						.anyMatch(f -> f.getFileName().toString().equals(name)
								|| f.getFileName().toString().startsWith(name + "."));
		} catch (IOException e) {
			return false;
		}
	}
}

@CommandLine.Command(name = "setup", description = "Make jbang commands available for the user.")
class AppSetup extends BaseCommand {

	@CommandLine.Option(names = {
			"--java" }, description = "Add Jbang's Java to the user's environment as well", negatable = true)
	Boolean java;

	@CommandLine.Option(names = {
			"--force" }, description = "Force setup to be performed even when existing configuration has been detected")
	boolean force;

	@Override
	public Integer doCall() {
		boolean withJava;
		if (java == null) {
			withJava = guessWithJava();
		} else {
			withJava = java;
		}
		return setup(withJava, force, true);
	}

	public static boolean needsSetup() {
		String envPath = System.getenv("PATH");
		Path binDir = Settings.getConfigBinDir();
		return !envPath.toLowerCase().contains(binDir.toString().toLowerCase());
	}

	/**
	 * Makes a best guess if JAVA_HOME should be set by us or not. Returns true if
	 * no JAVA_HOME is set and javac wasn't found on the PATH and we have at least
	 * one managed JDK installed by us. Otherwise it returns false.
	 */
	public static boolean guessWithJava() {
		boolean withJava;
		int v = JdkManager.getDefaultJdk();
		String javaHome = System.getenv("JAVA_HOME");
		Path javacCmd = Util.searchPath("javac");
		withJava = (v > 0
				&& (javaHome == null
						|| javaHome.isEmpty()
						|| javaHome.toLowerCase().startsWith(Settings.getConfigDir().toString().toLowerCase()))
				&& (javacCmd == null || javacCmd.startsWith(Settings.getConfigBinDir())));
		return withJava;
	}

	public static int setup(boolean withJava, boolean force, boolean chatty) {
		Path jdkHome = null;
		if (withJava) {
			int v = JdkManager.getDefaultJdk();
			if (v < 0) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
				return EXIT_UNEXPECTED_STATE;
			}
			jdkHome = Settings.getCurrentJdkDir();
		}

		Path binDir = Settings.getConfigBinDir();
		binDir.toFile().mkdirs();

		boolean changed = false;
		String cmd = "";
		// Permanently add Jbang's bin folder to the user's PATH
		if (Util.isWindows()) {
			String env = "";
			if (withJava) {
				String newPath = jdkHome.resolve("bin") + ";";
				env += " ; [Environment]::SetEnvironmentVariable('Path', '" + newPath + "' + " +
						"[Environment]::GetEnvironmentVariable('Path', [EnvironmentVariableTarget]::User), " +
						"[EnvironmentVariableTarget]::User)";
				env += " ; [Environment]::SetEnvironmentVariable('JAVA_HOME', '" + jdkHome + "', " +
						"[EnvironmentVariableTarget]::User)";
			}
			if (force || needsSetup()) {
				// Create the command to change the user's PATH
				String newPath = binDir + ";";
				env += " ; [Environment]::SetEnvironmentVariable('Path', '" + newPath + "' + " +
						"[Environment]::GetEnvironmentVariable('Path', [EnvironmentVariableTarget]::User), " +
						"[EnvironmentVariableTarget]::User)";
			}
			if (!env.isEmpty()) {
				if (Util.isUsingPowerShell()) {
					cmd = "{ " + env + " }";
				} else {
					cmd = "powershell -NoProfile -ExecutionPolicy Bypass -NonInteractive -Command \"" + env + "\" & ";
				}
				changed = true;
			}
		} else {
			if (force || needsSetup() || withJava) {
				// Update shell startup scripts
				Path bashRcFile = getHome().resolve(".bashrc");
				changed = changeScript(binDir, jdkHome, bashRcFile) || changed;
				Path zshRcFile = getHome().resolve(".zshrc");
				changed = changeScript(binDir, jdkHome, zshRcFile) || changed;
			}
		}

		if (changed) {
			Util.infoMsg("Setting up Jbang environment...");
		} else if (chatty) {
			Util.infoMsg("Jbang environment is already set up.");
		}
		if (Util.isWindows()) {
			if (changed) {
				if (Util.isUsingPowerShell()) {
					System.err.println("Please start a new PowerShell for changes to take effect");
				} else {
					System.err.println("Please open a new CMD window for changes to take effect");
				}
			}
			System.out.println(cmd);
			return EXIT_EXECUTE;
		} else {
			if (changed) {
				System.out.println("Please start a new Shell for changes to take effect");
			}
			return EXIT_OK;
		}
	}

	private static boolean changeScript(Path binDir, Path javaHome, Path bashFile) {
		try {
			// Detect if Jbang has already been set up before
			boolean jbangFound = Files.exists(bashFile)
					&& Files.lines(bashFile)
							.anyMatch(ln -> ln.trim().startsWith("#") && ln.toLowerCase().contains("jbang"));
			if (!jbangFound) {
				// Add lines to add Jbang to PATH
				String lines = "\n# Add Jbang to environment\n" +
						"alias j!=jbang\n";
				if (javaHome != null) {
					lines += "export PATH=\"" + toHomePath(binDir) + ":" + toHomePath(javaHome.resolve("bin"))
							+ ":$PATH\"\n" +
							"export JAVA_HOME=" + toHomePath(javaHome) + "\n";
				} else {
					lines += "export PATH=\"" + toHomePath(binDir) + ":$PATH\"\n";
				}
				Files.write(bashFile, lines.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				Util.verboseMsg("Added Jbang setup lines " + bashFile);
				return true;
			}
		} catch (IOException e) {
			Util.verboseMsg("Couldn't change script: " + bashFile, e);
		}
		return false;
	}

	private static Path getHome() {
		return Paths.get(System.getProperty("user.home"));
	}

	private static String toHomePath(Path path) {
		Path home = getHome();
		if (path.startsWith(home)) {
			if (Util.isWindows()) {
				return "%userprofile%\\" + home.relativize(path);
			} else {
				return "$HOME/" + home.relativize(path);
			}
		} else {
			return path.toString();
		}
	}
}
