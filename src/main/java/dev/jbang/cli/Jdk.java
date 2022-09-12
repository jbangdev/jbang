package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

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
					"-f" }, description = "Force installation even when already installed") boolean force,
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") int version,
			@CommandLine.Parameters(paramLabel = "existingJdkPath", index = "1", description = "Pre installed JDK path", arity = "0..1") String path)
			throws IOException {
		if (force || !JdkManager.isInstalledJdk(version)) {
			if (!Util.isNullOrBlankString(path)) {
				JdkManager.linkToExistingJdk(path, version);
			} else {
				JdkManager.downloadAndInstallJdk(version);
			}
		} else {
			Util.infoMsg("JDK " + version + " is already installed");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Lists installed JDKs.")
	public Integer list(
			@CommandLine.Option(names = {
					"--available" }, description = "Shows versions available for installation") boolean available,
			@CommandLine.Option(names = {
					"--format" }, description = "Specify output format ('text' or 'json')") FormatMixin.Format format) {
		int defaultJdk = JdkManager.getDefaultJdk();
		PrintStream out = System.out;
		final Set<Integer> installedJdks = JdkManager.listInstalledJdks();
		final Set<Integer> jdks = available ? JdkManager.listAvailableJdks() : installedJdks;
		List<JdkOut> jdkOuts = jdks	.stream()
									.map(jdk -> new JdkOut(jdk, installedJdks.contains(jdk), jdk == defaultJdk))
									.collect(Collectors.toList());
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(jdkOuts, out);
		} else {
			if (!jdkOuts.isEmpty()) {
				if (available) {
					out.println("Available JDKs (*=installed, <=default):");
				} else {
					out.println("Installed JDKs (<=default):");
				}
				jdkOuts.forEach(jdk -> {
					out.print("  " + jdk.version);
					if (jdk.version == defaultJdk) {
						out.print(" <");
					} else if (available && installedJdks.contains(jdk.version)) {
						out.print(" *");
					}
					out.println();
				});
			} else {
				out.println(String.format("No JDKs %s", available ? "available" : "installed"));
			}
		}
		return EXIT_OK;
	}

	static class JdkOut {
		int version;
		String javaHomeDir;
		@SerializedName("default")
		Boolean isDefault;

		public JdkOut(int version, boolean isInstalled, boolean isDefault) {
			this.version = version;
			if (isInstalled) {
				try {
					this.javaHomeDir = JdkManager.getJdkPath(version).toRealPath().toString();
				} catch (IOException e) {
					this.javaHomeDir = JdkManager.getJdkPath(version).toString();
				}
			}
			if (isDefault) {
				this.isDefault = true;
			}
		}
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
			String homeOsStr = Util.pathToOsString(home);
			PrintStream out = System.out;
			switch (Util.getShell()) {
			case bash:
				// Not using `println()` here because it will output /n/r
				// on Windows which causes problems
				out.print("export PATH=\"" + homeStr + "/bin:$PATH\"\n");
				out.print("export JAVA_HOME=\"" + homeOsStr + "\"\n");
				out.print("# Run this command to configure your shell:\n");
				out.print("# eval $(jbang jdk java-env");
				if (version != null) {
					out.print(" " + version);
				}
				out.print(")\n");
				break;
			case cmd:
				out.println("set PATH=" + homeStr + "\\bin;%PATH%");
				out.println("set JAVA_HOME=" + homeOsStr);
				out.println("rem Copy & paste the above commands in your CMD window or add");
				out.println("rem them to your Environment Variables in the System Settings.");
				break;
			case powershell:
				out.println("$env:PATH=\"" + homeStr + "\\bin:$env:PATH\"");
				out.println("$env:JAVA_HOME=\"" + homeOsStr + "\"");
				out.println("# Run this command to configure your environment:");
				out.print("# jbang jdk java-env");
				if (version != null) {
					out.print(" " + version);
				}
				out.println(" | iex");
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
