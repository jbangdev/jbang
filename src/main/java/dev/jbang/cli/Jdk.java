package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.net.JdkManager;
import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;
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
		JdkProvider.Jdk defaultJdk = JdkManager.getDefaultJdk();
		PrintStream out = System.out;
		SortedSet<JdkProvider.Jdk> jdks;
		if (available) {
			jdks = JdkManager.listAvailableJdks();
		} else {
			jdks = JdkManager.listInstalledJdks();
		}
		List<JdkOut> jdkOuts = jdks	.stream()
									.map(jdk -> new JdkOut(jdk.id, jdk.version, jdk.home, jdk.equals(defaultJdk)))
									.collect(Collectors.toList());
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(jdkOuts, out);
		} else {
			if (!jdkOuts.isEmpty()) {
				if (!available) {
					out.println("Installed JDKs (<=default):");
				}
				jdkOuts.forEach(jdk -> {
					out.print("   ");
					/*
					 * Uncomment if we ever have multiple updatable jdk providers if (available) {
					 * out.print(jdk.id + " = "); }
					 */
					out.print(jdk.version);
					out.print(" (" + jdk.fullVersion + ")");
					if (!available) {
						if (Boolean.TRUE.equals(jdk.isDefault)) {
							out.print(" <");
						}
					}
					out.println();
				});
			} else {
				out.printf("No JDKs %s%n", available ? "available" : "installed");
			}
		}
		return EXIT_OK;
	}

	static class JdkOut implements Comparable<JdkOut> {
		String id;
		int version;
		String fullVersion;
		String javaHomeDir;
		@SerializedName("default")
		Boolean isDefault;

		public JdkOut(String id, String version, Path home, boolean isDefault) {
			this.id = id;
			this.version = JavaUtil.parseJavaVersion(version);
			this.fullVersion = version;
			if (home != null) {
				try {
					this.javaHomeDir = home.toRealPath().toString();
				} catch (IOException e) {
					this.javaHomeDir = home.toString();
				}
			}
			if (isDefault) {
				this.isDefault = true;
			}
		}

		@Override
		public int compareTo(JdkOut o) {
			if (version != o.version) {
				return Integer.compare(version, o.version);
			} else {
				return id.compareTo(o.id);
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
			JdkProvider.Jdk jdk = JdkManager.getDefaultJdk();
			if (jdk == null) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
				return null;
			}
			home = Settings.getCurrentJdkDir();
		} else {
			JdkProvider.Jdk jdk = JdkManager.getInstalledJdk(version);
			home = jdk != null ? jdk.home : null;
		}
		return home;
	}

	@CommandLine.Command(name = "default", description = "Sets the default JDK to be used by JBang.")
	public Integer defaultJdk(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version of the JDK to select", arity = "0..1") Integer version)
			throws IOException {
		JdkProvider.Jdk jdk = JdkManager.getDefaultJdk();
		if (version != null) {
			if (jdk.getMajorVersion() != version) {
				JdkManager.setDefaultJdk(version);
			} else {
				Util.infoMsg("Default JDK already set to " + jdk.getMajorVersion());
			}
		} else {
			if (jdk == null) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
			} else {
				Util.infoMsg("Default JDK is currently set to " + jdk.getMajorVersion());
			}
		}
		return EXIT_OK;
	}

}
