package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import dev.jbang.JdkManager;
import dev.jbang.Settings;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "setup", description = "Make jbang available for the user, either in the current session or permanently")
public class Setup extends BaseCommand {

	@CommandLine.Option(names = {
			"--java" }, description = "Add Jbang's Java to the user's environment as well", negatable = true)
	Boolean java;

	@CommandLine.Option(names = {
			"--force" }, description = "Force setup to be performed even when existing configuration has been detected")
	boolean force;

	@Override
	public Integer doCall() throws IOException {
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

	public static int setup(boolean withJava, boolean force, boolean chatty) throws IOException {
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

	private static boolean changeScript(Path binDir, Path javaHome, Path bashFile) throws IOException {
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
		} else {
			return false;
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
}
