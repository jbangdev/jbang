package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;

import dev.jbang.ExitException;
import dev.jbang.JdkManager;
import dev.jbang.Settings;
import dev.jbang.UnpackUtil;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "setup", description = "Make jbang available for the user, either in the current session or permanently")
public class Setup extends BaseCommand {
	private static final String jbangUrl = "https://github.com/jbangdev/jbang/releases/latest/download/jbang.zip";

	@CommandLine.Option(names = {
			"--with-java" }, description = "Add Jbang's Java to the user's environment as well")
	boolean withJava;

	@CommandLine.Option(names = {
			"--fresh" }, description = "Force re-download and re-install of jbang")
	boolean fresh;

	@Override
	public Integer doCall() throws IOException {
		return setup(withJava, fresh);
	}

	public static int setup(boolean withJava, boolean fresh) throws IOException {
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
		if (fresh || !Files.exists(binDir.resolve("jbang.jar"))) {
			// Download Jbang and unzip to ~/.jbang/bin/
			Util.infoMsg("Downloading and installing Jbang...");
			Path zipFile = Util.downloadAndCacheFile(jbangUrl, fresh);
			Path urlsDir = Settings.getCacheDir(Settings.CacheClass.urls);
			Util.deletePath(urlsDir.resolve("jbang"), true);
			UnpackUtil.unpack(zipFile, urlsDir);
			deleteJbangFiles(binDir);
			Path fromDir = urlsDir.resolve("jbang").resolve("bin");
			if (Util.isWindows() && Files.exists(binDir.resolve("jbang.jar")) && isRunningFromConfigBin()) {
				// On Windows we cannot update files that are in use, so we put
				// the files in a new folder and add an update script which will
				// get executed the next time the user starts Jbang
				Path toDir = binDir.resolveSibling("bin.new");
				Util.deletePath(toDir, true);
				copyJbangFiles(fromDir, toDir);
				createUpdateScript(toDir);
			} else {
				copyJbangFiles(fromDir, binDir);
			}
		}
		String cmd = "";
		// Permanently add Jbang's bin folder to the user's PATH
		Util.infoMsg("Adding jbang to PATH...");
		if (Util.isWindows()) {
			// Create the command to change the user's PATH
			String newPath = binDir + ";";
			if (withJava) {
				newPath += jdkHome.resolve("bin") + ";";
			}
			String env = "[Environment]::SetEnvironmentVariable('Path', '" + newPath + "' + " +
					"[Environment]::GetEnvironmentVariable('Path', [EnvironmentVariableTarget]::User), " +
					"[EnvironmentVariableTarget]::User)";
			if (withJava) {
				env += " ; [Environment]::SetEnvironmentVariable('JAVA_HOME', '" + jdkHome + "', " +
						"[EnvironmentVariableTarget]::User)";
			}
			if (Util.isUsingPowerShell()) {
				cmd = "{ " + env + " }";
			} else {
				cmd = "powershell -NoProfile -ExecutionPolicy Bypass -NonInteractive -Command \"" + env + "\" & ";
			}
		} else {
			boolean updated = false;
			// Update shell startup scripts
			Path bashRcFile = getHome().resolve(".bashrc");
			if (bashRcFile.toFile().exists()) {
				changeScript(binDir, jdkHome, bashRcFile);
				updated = true;
			}
			Path zshRcFile = getHome().resolve(".zshrc");
			if (zshRcFile.toFile().exists()) {
				changeScript(binDir, jdkHome, zshRcFile);
				updated = true;
			}
			if (!updated) {
				Util.errorMsg("Did not find .bashrc nor .zshrc. jbang not able to be setup.");
				return EXIT_GENERIC_ERROR;
			}
		}
		if (Util.isWindows()) {
			if (Util.isUsingPowerShell()) {
				System.err.println("Please start a new PowerShell to begin using jbang");
			} else {
				System.err.println("Please open a new CMD window to begin using jbang");
			}
			System.out.println(cmd);
			return EXIT_EXECUTE;
		} else {
			System.out.println("Please start a new Shell to begin using jbang");
			return EXIT_OK;
		}
	}

	private static void changeScript(Path binDir, Path javaHome, Path bashFile) throws IOException {
		// Detect if Jbang has already been set up before
		boolean jbangFound = Files	.lines(bashFile)
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
		}
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

	private static boolean isRunningFromConfigBin() {
		boolean result;
		try {
			CodeSource codeSource = Setup.class.getProtectionDomain().getCodeSource();
			File jarFile = new File(codeSource.getLocation().toURI().getPath());
			result = jarFile.toPath().startsWith(Settings.getConfigBinDir());
		} catch (URISyntaxException e) {
			result = false;
		}
		Util.verboseMsg("Running from $JBANG_DIR/bin");
		return result;
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

	private static void createUpdateScript(Path dir) throws IOException {
		String cmd = "@echo off\n" +
				"move /y \"%~dp0jbang\" \"%~dp0..\\bin\\\" > nul 2>&1\n" +
				"move /y \"%~dp0jbang.*\" \"%~dp0..\\bin\\\" > nul 2>&1\n" +
				"call \"%~dp0..\\bin\\jbang.cmd\" %*";
		Files.write(dir.resolve("update.cmd"), cmd.getBytes(), StandardOpenOption.CREATE_NEW);
		String ps1 = "Move-Item -Path \"$PSScriptRoot\\jbang\" -Destination \"$PSScriptRoot\\bin\\\" >$null 2>&1\n" +
				"Move-Item -Path \"$PSScriptRoot\\jbang.*\" -Destination \"$PSScriptRoot\\bin\\\" >$null 2>&1\n" +
				". \"$PSScriptRoot\\..\\bin\\jbang.ps1\"";
		Files.write(dir.resolve("update.ps1"), ps1.getBytes(), StandardOpenOption.CREATE_NEW);
	}
}
