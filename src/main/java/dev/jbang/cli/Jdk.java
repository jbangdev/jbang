package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.jbang.net.JdkManager;
import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "jdk", description = "Manage Java Development Kits installed by jbang.")
public class Jdk {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Mixin
	JdkProvidersMixin jdkProvidersMixin;

	@CommandLine.Command(name = "install", description = "Installs a JDK.")
	public Integer install(
			@CommandLine.Option(names = { "--force",
					"-f" }, description = "Force installation even when already installed") boolean force,
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version or id to install", arity = "1") String versionOrId,
			@CommandLine.Parameters(paramLabel = "existingJdkPath", index = "1", description = "Pre installed JDK path", arity = "0..1") String path)
			throws IOException {
		jdkProvidersMixin.initJdkProviders();
		JdkProvider.Jdk jdk = JdkManager.getInstalledJdk(versionOrId, true);
		if (force || jdk == null) {
			if (!Util.isNullOrBlankString(path)) {
				JdkManager.linkToExistingJdk(path, Integer.parseInt(versionOrId));
			} else {
				if (jdk == null) {
					jdk = JdkManager.getJdk(versionOrId, true);
				}
				jdk.install();
			}
		} else {
			Util.infoMsg("JDK is already installed: " + jdk);
			Util.infoMsg("Use --force to install anyway");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Lists installed JDKs.")
	public Integer list(
			@CommandLine.Option(names = {
					"--available" }, description = "Shows versions available for installation") boolean available,
			@CommandLine.Option(names = {
					"--show-details" }, description = "Shows detailed information for each JDK (only when format=text)") boolean details,
			@CommandLine.Option(names = {
					"--format" }, description = "Specify output format ('text' or 'json')") FormatMixin.Format format) {
		jdkProvidersMixin.initJdkProviders();
		JdkProvider.Jdk defaultJdk = JdkManager.getDefaultJdk();
		int defMajorVersion = defaultJdk != null ? defaultJdk.getMajorVersion() : 0;
		PrintStream out = System.out;
		List<JdkProvider.Jdk> jdks;
		if (available) {
			jdks = JdkManager.listAvailableJdks();
		} else {
			jdks = JdkManager.listInstalledJdks();
		}
		List<JdkOut> jdkOuts = jdks	.stream()
									.map(jdk -> new JdkOut(jdk.getId(), jdk.getVersion(), jdk.getProvider().name(),
											jdk.getHome(),
											details ? jdk.equals(defaultJdk)
													: jdk.getMajorVersion() == defMajorVersion))
									.collect(Collectors.toList());
		if (!details) {
			// Only keep a list of unique major versions
			Set<JdkOut> uniqueJdks = new TreeSet<>(Comparator.comparingInt(j -> j.version));
			uniqueJdks.addAll(jdkOuts);
			jdkOuts = new ArrayList<>(uniqueJdks);
		}
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
					out.print(jdk.version);
					out.print(" (");
					out.print(jdk.fullVersion);
					if (details) {
						out.print(", " + jdk.providerName + ", " + jdk.id);
						if (jdk.javaHomeDir != null) {
							out.print(", " + jdk.javaHomeDir);
						}
					}
					out.print(")");
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
		String providerName;
		String javaHomeDir;
		@SerializedName("default")
		Boolean isDefault;

		public JdkOut(String id, String version, String providerName, Path home, boolean isDefault) {
			this.id = id;
			this.version = JavaUtil.parseJavaVersion(version);
			this.fullVersion = version;
			this.providerName = providerName;
			if (home != null) {
				this.javaHomeDir = home.toString();
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
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") String versionOrId) {
		jdkProvidersMixin.initJdkProviders();
		JdkProvider.Jdk jdk = JdkManager.getInstalledJdk(versionOrId, true);
		if (jdk == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "JDK " + versionOrId + " is not installed");
		}
		JdkManager.uninstallJdk(jdk);
		Util.infoMsg("Uninstalled JDK:\n  " + versionOrId);
		return EXIT_OK;
	}

	@CommandLine.Command(name = "home", description = "Prints the folder where the given JDK is installed.")
	public Integer home(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId) {
		jdkProvidersMixin.initJdkProviders();
		Path home = JdkManager.getOrInstallJdk(versionOrId).getHome();
		if (home != null) {
			String homeStr = Util.pathToString(home);
			System.out.println(homeStr);
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "java-env", aliases = "env", description = "Prints out the environment variables needed to use the given JDK.")
	public Integer javaEnv(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId) {
		jdkProvidersMixin.initJdkProviders();
		JdkProvider.Jdk jdk = null;
		if (versionOrId != null && JavaUtil.isRequestedVersion(versionOrId)) {
			jdk = JdkManager.getJdk(versionOrId, true);
		}
		if (jdk == null || !jdk.isInstalled()) {
			jdk = JdkManager.getOrInstallJdk(versionOrId);
		}
		Path home = jdk.getHome();
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
				if (versionOrId != null) {
					out.print(" " + versionOrId);
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
				out.println("$env:PATH=\"" + homeStr + "\\bin;$env:PATH\"");
				out.println("$env:JAVA_HOME=\"" + homeOsStr + "\"");
				out.println("# Run this command to configure your environment:");
				out.print("# jbang jdk java-env");
				if (versionOrId != null) {
					out.print(" " + versionOrId);
				}
				out.println(" | iex");
				break;
			}
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "default", description = "Sets the default JDK to be used by JBang.")
	public Integer defaultJdk(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId) {
		jdkProvidersMixin.initJdkProviders();
		JdkProvider.Jdk defjdk = JdkManager.getDefaultJdk();
		if (versionOrId != null) {
			JdkProvider.Jdk jdk = JdkManager.getOrInstallJdk(versionOrId);
			if (defjdk == null || (!jdk.equals(defjdk) && !Objects.equals(jdk.getHome(), defjdk.getHome()))) {
				JdkManager.setDefaultJdk(jdk);
			} else {
				Util.infoMsg("Default JDK already set to " + defjdk.getMajorVersion());
			}
		} else {
			if (defjdk == null) {
				Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
			} else {
				Util.infoMsg("Default JDK is currently set to " + defjdk.getMajorVersion());
			}
		}
		return EXIT_OK;
	}

}
