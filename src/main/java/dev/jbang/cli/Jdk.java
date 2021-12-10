package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Set;

import dev.jbang.Settings;
import dev.jbang.net.JdkManager;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "jdk", description = "Manage Java Development Kits installed by jbang.")
public class Jdk {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "install", description = "Installs a JDK.")
	public Integer install(
			@CommandLine.Option(names = { "--force",
					"-f" }, description = "Force installation even when already installed", defaultValue = "false") boolean force,
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") int version)
			throws IOException {
		if (force || !JdkManager.isInstalledJdk(version)) {
			JdkManager.downloadAndInstallJdk(version);
		} else {
			Util.infoMsg("JDK " + version + " is already installed");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Lists installed JDKs.")
	public Integer list() {
		int v = JdkManager.getDefaultJdk();
		PrintWriter err = spec.commandLine().getErr();
		final Set<Integer> installedJdks = JdkManager.listInstalledJdks();
		if (!installedJdks.isEmpty()) {
			Util.infoMsg("Available installed JDKs:");
			installedJdks.forEach(jdk -> {
				if (jdk == v) {
					err.print(" *");
				}
				err.print("  " + jdk);

				err.println();
			});
		} else {
			Util.infoMsg("No JDKs installed");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "uninstall", description = "Uninstalls an existing JDK.")
	public Integer uninstall(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") int version) {
		if (JdkManager.isInstalledJdk(version)) {
			JdkManager.uninstallJdk(version);
			Util.infoMsg("Uninstalled JDK:\n  " + version);
		} else {
			Util.infoMsg("JDK " + version + " is not installed");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "home", description = "Prints the folder where the given JDK is installed.")
	public Integer home(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version of the JDK to select", arity = "0..1") Integer version) {
		Path home = getJdkPath(version);
		String homeStr = Util.pathToString(home);
		System.out.println(homeStr);
		return EXIT_OK;
	}

	@CommandLine.Command(name = "java-env", description = "Prints out the environment variables needed to use the given JDK.")
	public Integer javaEnv(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version of the JDK to select", arity = "0..1") Integer version) {
		Path home = getJdkPath(version);
		if (home != null) {
			String homeStr = Util.pathToString(home);
			PrintStream out = System.out;
			switch (Util.getShell()) {
			case bash:
				out.println("export PATH=\"" + homeStr + "/bin:$PATH\"");
				out.println("export JAVA_HOME=\"" + homeStr + "\"");
				out.println("# Run this command to configure your shell:");
				out.print("# eval $(jbang jdk java-env");
				if (version != null) {
					out.print(" " + version);
				}
				out.println(")");
				break;
			case cmd:
				out.println("set PATH=" + homeStr + "\\bin;%PATH%");
				out.println("set JAVA_HOME=" + homeStr);
				out.println("rem Copy & paste the above commands in your CMD window or add");
				out.println("rem them to your Environment Variables in the System Settings.");
				break;
			case powershell:
				out.println("$env:PATH=\"" + homeStr + "\\bin:$env:PATH\"");
				out.println("$env:JAVA_HOME=\"" + homeStr + "\"");
				out.println("# Copy & paste the above commands in your Powershell window or add");
				out.println("# them to your Environment Variables in the System Settings.");
				break;
			}
		}
		return EXIT_OK;
	}

	private Path getJdkPath(Integer version) {
		Path home;
		if (version == null) {
			int v = JdkManager.getDefaultJdk();
			if (v < 0) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
				return null;
			}
			home = Settings.getCurrentJdkDir();
		} else {
			home = JdkManager.getInstalledJdk(version);
		}
		return home;
	}

	@CommandLine.Command(name = "default", description = "Sets the default JDK to be used by JBang.")
	public Integer defaultJdk(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version of the JDK to select", arity = "0..1") Integer version)
			throws IOException {
		int v = JdkManager.getDefaultJdk();
		if (version != null) {
			if (v != version) {
				JdkManager.setDefaultJdk(version);
			} else {
				Util.infoMsg("Default JDK already set to " + v);
			}
		} else {
			if (v < 0) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
			} else {
				Util.infoMsg("Default JDK is currently set to " + v);
			}
		}
		return EXIT_OK;
	}

}
