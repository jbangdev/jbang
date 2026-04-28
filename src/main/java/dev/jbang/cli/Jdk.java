package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

@GroupCommandDefinition(name = "jdk", description = "Manage Java Development Kits installed by jbang.", groupCommands = {
		Jdk.JdkInstall.class, Jdk.JdkList.class, Jdk.JdkUninstall.class,
		Jdk.JdkHome.class, Jdk.JdkJavaEnv.class, Jdk.JdkExec.class,
		Jdk.JdkDefault.class }, generateHelp = true, helpGroup = "Caching", defaultValueProvider = JBangDefaultValueProvider.class)
public class Jdk extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
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

	@CommandDefinition(name = "install", description = "Installs a JDK.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkInstall extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(shortName = 'f', name = "force", hasValue = false, description = "Force installation even when already installed")
		boolean force;

		@Argument(description = "The version or id to install", required = true)
		String versionOrId;

		@Option(name = "path", description = "Pre installed JDK path")
		String path;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk jdk = jdkMan.getInstalledJdk(versionOrId, JdkProvider.Predicates.canUpdate);
			if (force || jdk == null) {
				if (!Util.isNullOrBlankString(path)) {
					jdkMan.linkToExistingJdk(Paths.get(path), versionOrId);
				} else {
					if (jdk == null) {
						jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canUpdate);
						if (jdk == null) {
							throw new ExitException(EXIT_INVALID_INPUT,
									"JDK is not available for installation: " + versionOrId);
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
	}

	@CommandDefinition(name = "list", description = "Lists installed JDKs.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkList extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(name = "available", hasValue = false, description = "Shows versions available for installation")
		boolean available;

		@Option(name = "show-details", hasValue = false, description = "Shows detailed information for each JDK (only when format=text)")
		boolean details;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		String format;

		@Override
		public Integer doCall() throws IOException {
			validateFormat(format);
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk defaultJdk = jdkMan.getDefaultJdk();
			int defMajorVersion = defaultJdk != null ? defaultJdk.majorVersion() : 0;
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
								: jdk.majorVersion() == defMajorVersion))
				.collect(Collectors.toList());
			if (!details) {
				// Only keep a list of unique major versions
				Set<JdkOut> uniqueJdks = new TreeSet<>(Comparator.comparingInt(j -> j.version));
				uniqueJdks.addAll(jdkOuts);
				jdkOuts = new ArrayList<>(uniqueJdks);
			}
			if ("json".equals(format)) {
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
	}

	@CommandDefinition(name = "uninstall", description = "Uninstalls an existing JDK.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkUninstall extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(description = "The version to uninstall", required = true)
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getInstalledJdk(versionOrId,
					JdkProvider.Predicates.canUpdate);
			if (jdk == null) {
				throw new ExitException(EXIT_INVALID_INPUT, "JDK " + versionOrId + " is not installed");
			}
			jdkMan.uninstallJdk(jdk);
			Util.infoMsg("Uninstalled JDK:\n  " + versionOrId);
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "home", description = "Prints the folder where the given JDK is installed.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkHome extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(description = "The version of the JDK to select")
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getOrInstallJdk(versionOrId);
			if (jdk.isInstalled()) {
				Path home = jdk.home();
				String homeStr = Util.pathToString(home);
				System.out.println(homeStr);
			}
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "java-env", description = "Prints out the environment variables needed to use the given JDK.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkJavaEnv extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(description = "The version of the JDK to select")
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk jdk = null;
			if (versionOrId != null && JavaUtil.isRequestedVersion(versionOrId)) {
				jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canUpdate);
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
	}

	@CommandDefinition(name = "exec", description = "Executes the given command using the default (or specified) JDK.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkExec extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(shortName = 'j', name = "java", description = "JDK version to use for executing the command.")
		String versionOrId;

		@Arguments(description = "Command to execute", required = true)
		List<String> args;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
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
	}

	@CommandDefinition(name = "default", description = "Sets the default JDK to be used by JBang.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class JdkDefault extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(description = "The version of the JDK to select")
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
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
				if (defjdk == null) {
					Util.infoMsg("No default JDK set, use 'jbang jdk default <version>' to set one.");
				} else {
					Util.infoMsg("Default JDK is currently set to " + defjdk.majorVersion());
				}
			}
			return EXIT_OK;
		}
	}
}
