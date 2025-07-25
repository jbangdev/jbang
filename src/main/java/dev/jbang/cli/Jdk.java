package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "jdk", description = "Manage Java Development Kits installed by jbang.")
public class Jdk {

	@CommandLine.Mixin
	HelpMixin helpMixin;

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Mixin
	JdkProvidersMixin jdkProvidersMixin;

	@CommandLine.Command(name = "install", aliases = "i", description = "Installs a JDK.")
	public Integer install(
			@CommandLine.Option(names = { "--force",
					"-f" }, description = "Force installation even when already installed") boolean force,
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version or id to install", arity = "1") String versionOrId,
			@CommandLine.Parameters(paramLabel = "existingJdkPath", index = "1", description = "Pre installed JDK path", arity = "0..1") String path)
			throws IOException {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		dev.jbang.devkitman.Jdk jdk = jdkMan.getInstalledJdk(versionOrId, JdkProvider.Predicates.canInstall);
		if (force || jdk == null) {
			if (!Util.isNullOrBlankString(path)) {
				jdkMan.linkToExistingJdk(Paths.get(path), versionOrId);
			} else {
				if (jdk == null) {
					jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canInstall);
					if (jdk == null) {
						throw new IllegalArgumentException("JDK is not available for installation: " + versionOrId);
					}
				}
				if (!jdk.isInstalled()) {
					((dev.jbang.devkitman.Jdk.AvailableJdk) jdk).install();
				}
			}
		} else {
			Util.infoMsg("JDK is already installed: " + jdk);
			Util.infoMsg("Use --force to install anyway");
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", aliases = "l", description = "Lists installed JDKs.")
	public Integer list(
			@CommandLine.Option(names = {
					"--available", "-a" }, description = "Shows versions available for installation") boolean available,
			@CommandLine.Option(names = {
					"--show-details", "--details",
					"-d" }, description = "Shows detailed information for each JDK (only when format=text)") boolean details,
			@CommandLine.Option(names = {
					"--format" }, description = "Specify output format ('text' or 'json')") FormatMixin.Format format) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		dev.jbang.devkitman.Jdk defaultJdk = jdkMan.getDefaultJdk();
		String defVersion = defaultJdk != null ? defaultJdk.version() : "";
		PrintStream out = System.out;
		List<? extends dev.jbang.devkitman.Jdk> jdks;
		if (available) {
			jdks = jdkMan.listAvailableJdks();
		} else {
			jdks = jdkMan.listInstalledJdks();
		}
		List<JdkOut> jdkOuts = jdks.stream()
			.map(jdk -> new JdkOut(jdk.id(), jdk.version(), jdk.provider().name(),
					jdk.isInstalled() ? ((dev.jbang.devkitman.Jdk.InstalledJdk) jdk).home() : null,
					details ? jdk.equals(defaultJdk)
							: jdk.version().equals(defVersion),
					jdk.tags()))
			.collect(Collectors.toList());
		if (!details) {
			// Only keep a list of unique versions
			Set<JdkOut> uniqueJdks = new TreeSet<>(Comparator.<JdkOut>comparingInt(j -> j.version).reversed());
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
					if (details) {
						out.print(jdk.fullVersion);
						out.print(", " + jdk.providerName + ", " + jdk.id);
						if (jdk.javaHomeDir != null) {
							out.print(", " + jdk.javaHomeDir);
						}
						if (!jdk.tags.isEmpty()) {
							out.print(", " + jdk.tags);
						}
					} else {
						out.print(available ? jdk.id : jdk.fullVersion);
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
		String realHomeDir;
		@SerializedName("default")
		Boolean isDefault;
		Set<String> tags;

		public JdkOut(String id, String version, String providerName, Path home, boolean isDefault, Set<String> tags) {
			this.id = id;
			this.version = JavaUtil.parseJavaVersion(version);
			this.fullVersion = version;
			this.providerName = providerName;
			if (home != null) {
				this.javaHomeDir = home.toString();
				this.realHomeDir = home.toString();
				try {
					this.realHomeDir = home.toRealPath().toString();
				} catch (IOException e) {
					// Ignore
				}
			}
			if (isDefault) {
				this.isDefault = true;
			}
			this.tags = tags != null ? trimTags(tags) : Collections.emptySet();
		}

		private Set<String> trimTags(Set<String> tags) {
			Set<String> trimmedTags = new HashSet<>();
			for (String tag : tags) {
				if (!tag.equalsIgnoreCase("ga")
						&& !tag.equalsIgnoreCase("jdk")) {
					trimmedTags.add(tag.toLowerCase());
				}
			}
			return trimmedTags;
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

	@CommandLine.Command(name = "uninstall", aliases = "u", description = "Uninstalls an existing JDK.")
	public Integer uninstall(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version to install", arity = "1") String versionOrId) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		// This will first select for JDKs from providers that can actually install JDKs
		dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getInstalledJdk(versionOrId,
				JdkProvider.Predicates.canInstall);
		if (jdk == null) {
			// If necessary we select JDKs from providers that can update JDKs
			jdk = jdkMan.getInstalledJdk(versionOrId, JdkProvider.Predicates.canUpdate);
			if (jdk == null) {
				throw new ExitException(EXIT_INVALID_INPUT, "JDK " + versionOrId + " is not installed");
			}
		}
		jdk.uninstall();
		Util.infoMsg("Uninstalled JDK:\n  " + jdk.id());
		return EXIT_OK;
	}

	@CommandLine.Command(name = "home", description = "Prints the folder where the given JDK is installed.")
	public Integer home(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getOrInstallJdk(versionOrId);
		if (jdk.isInstalled()) {
			Path home = jdk.home();
			String homeStr = Util.pathToString(home);
			System.out.println(homeStr);
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "java-env", aliases = "env", description = "Prints out the environment variables needed to use the given JDK.")
	public Integer javaEnv(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		dev.jbang.devkitman.Jdk jdk = null;
		if (versionOrId != null && JavaUtil.isRequestedVersion(versionOrId)) {
			jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canInstall);
		}
		if (jdk == null || !jdk.isInstalled()) {
			jdk = jdkMan.getOrInstallJdk(versionOrId);
		}
		if (jdk.isInstalled()) {
			Path home = ((dev.jbang.devkitman.Jdk.InstalledJdk) jdk).home();
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

	@CommandLine.Command(name = "exec", aliases = "x", description = "Executes the given command using the default (or specified) JDK.")
	public Integer exec(
			@CommandLine.Option(names = { "-j",
					"--java" }, description = "JDK version to use for executing the command.") String versionOrId,
			@CommandLine.Parameters(index = "0..*", arity = "1..*", description = "Command to execute") List<String> args) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		dev.jbang.devkitman.Jdk jdk = null;
		if (versionOrId != null && JavaUtil.isRequestedVersion(versionOrId)) {
			jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canUpdate);
		}
		if (jdk == null || !jdk.isInstalled()) {
			jdk = jdkMan.getOrInstallJdk(versionOrId);
		}
		if (jdk.isInstalled()) {
			Path home = ((dev.jbang.devkitman.Jdk.InstalledJdk) jdk).home();
			String fullCmd = CommandBuffer.of(args).asCommandLine();
			if (Util.getShell() == Util.Shell.bash) {
				fullCmd = "env PATH=\"" + home + File.separator + "bin:$PATH\" JAVA_HOME='" + home + "' "
						+ fullCmd;
			} else if (Util.getShell() == Util.Shell.powershell) {
				fullCmd = "{ $oldPath, $env:PATH, $oldHome, $env:JAVA_HOME=$env:PATH, \"" + home
						+ "\\bin;$env:PATH\", $env:JAVA_HOME, '" + home + "' ; " + fullCmd
						+ " ; $env:PATH, $env:JAVA_HOME=$oldPath, $oldHome }";
			} else {
				String path = home + "\\bin;" + System.getenv("PATH");
				fullCmd = "set \"PATH=" + path + "\" && set \"JAVA_HOME=" + home + "\" && " + fullCmd;
			}
			Util.verboseMsg("Executing in Java environment: " + fullCmd);
			System.out.println(fullCmd);
			return EXIT_EXECUTE;
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "default", description = "Sets the default JDK to be used by JBang.")
	public Integer defaultJdk(
			@CommandLine.Parameters(paramLabel = "versionOrId", index = "0", description = "The version of the JDK to select", arity = "0..1") String versionOrId,
			@CommandLine.Option(names = {
					"--show-details", "--details",
					"-d" }, description = "Shows detailed information for each JDK (only when format=text)") boolean details,
			@CommandLine.Option(names = {
					"--format" }, description = "Specify output format ('text' or 'json')") FormatMixin.Format format) {
		JdkManager jdkMan = jdkProvidersMixin.getJdkManager();
		if (!jdkMan.hasDefaultProvider()) {
			Util.warnMsg("Cannot perform operation, the 'default' provider was not found");
			return EXIT_INVALID_INPUT;
		}
		dev.jbang.devkitman.Jdk.InstalledJdk defjdk = jdkMan.getDefaultJdk();
		if (versionOrId != null) {
			dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getOrInstallJdk(versionOrId);
			if (defjdk == null || (!jdk.equals(defjdk) && !Objects.equals(jdk.home(), defjdk.home()))) {
				jdkMan.setDefaultJdk(jdk);
			} else {
				Util.infoMsg("Default JDK already set to " + defjdk.majorVersion());
			}
		} else {
			List<dev.jbang.devkitman.Jdk.InstalledJdk> jdks = jdkMan.listDefaultJdks();
			List<JdkOut> jdkOuts = jdks.stream()
				.map(jdk -> new JdkOut(jdk.id(), jdk.version(), jdk.provider().name(), jdk.home(),
						jdk.id().equals("default"), jdk.tags()))
				.collect(Collectors.toList());
			PrintStream out = System.out;
			if (format == FormatMixin.Format.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(jdkOuts, out);
			} else {
				if (!jdkOuts.isEmpty()) {
					out.println("Default JDKs:");
					jdkOuts.forEach(jdk -> {
						out.print("   ");
						if (Boolean.TRUE.equals(jdk.isDefault)) {
							out.print("*");
						} else {
							out.print(jdk.version);
						}
						out.print(" -> ");
						out.print(jdk.realHomeDir);
						if (details) {
							out.print(" (" + jdk.fullVersion + ", " + jdk.id);
							if (!jdk.tags.isEmpty()) {
								out.print(", " + jdk.tags);
							}
							out.print(")");
						}
						out.println();
					});
				} else {
					out.println("No default JDK set, use 'jbang jdk default <version>' to set one.");
				}
			}
		}
		return EXIT_OK;
	}
}
