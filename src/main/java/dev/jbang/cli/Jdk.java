package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.devkitman.JdkDistroQuery;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.util.UnpackUtils;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.NetUtil;
import dev.jbang.util.Util;

@CommandDefinition(name = "jdk", description = "Manage Java Development Kits installed by jbang.", groupCommands = {
		Jdk.JdkInstall.class, Jdk.JdkList.class, Jdk.JdkUninstall.class,
		Jdk.JdkHome.class, Jdk.JdkJavaEnv.class, Jdk.JdkExec.class,
		Jdk.JdkDefault.class }, generateHelp = true, helpGroup = "Caching")
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
		String realHomeDir;
		String linkedId;
		@SerializedName("default")
		boolean isDefault;
		Set<String> tags;

		public JdkOut(String id, String version, String providerName, Path home, String linkedId,
				boolean isDefault, Set<String> tags) {
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
			this.linkedId = linkedId;
			if (isDefault) {
				this.isDefault = true;
			}
			this.tags = tags != null ? trimTags(tags) : Collections.emptySet();
		}

		private Set<String> trimTags(Set<String> tags) {
			Set<String> trimmed = new HashSet<>();
			for (String tag : tags) {
				if (!tag.equalsIgnoreCase("ga") && !tag.equalsIgnoreCase("jdk")) {
					trimmed.add(tag.toLowerCase());
				}
			}
			return trimmed;
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

	@CommandDefinition(name = "install", aliases = {
			"i" }, description = "Installs a JDK.", generateHelp = true)
	public static class JdkInstall extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(shortName = 'f', name = "force", hasValue = false, description = "Force installation even when already installed")
		boolean force;

		@Argument(paramLabel = "versionOrId", index = "0", arity = "1", description = "The version or id to install", required = true)
		String versionOrId;

		@Argument(paramLabel = "pathOrUrl", index = "1", arity = "0..1", description = "Pre installed JDK path or URL/path to installable archive")
		String pathOrUrl;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk jdk = jdkMan.getInstalledJdk(versionOrId, JdkProvider.Predicates.canInstall);
			if (force || jdk == null) {
				if (jdk != null) {
					((dev.jbang.devkitman.Jdk.InstalledJdk) jdk).uninstall();
				}
				if (!Util.isNullOrBlankString(pathOrUrl)) {
					if (isRemoteUrl(pathOrUrl)) {
						installJdkFromUrl(pathOrUrl);
					} else {
						Path jdkPath = parseLocalPath(pathOrUrl);
						if (Files.isRegularFile(jdkPath)) {
							installJdkFromArchive(jdkPath);
						} else {
							if (isValidInteger(versionOrId)) {
								versionOrId = generateUserId(versionOrId);
								Util.infoMsg("Numeric id detected, using '" + versionOrId + "' as id");
							}
							Path jdkCacheDir = Settings.getCacheDir(Cache.CacheClass.jdks);
							if (jdkPath.toAbsolutePath().startsWith(jdkCacheDir.toAbsolutePath())) {
								throw new IllegalArgumentException(
										"The provided path cannot point to a JBang managed JDK");
							}
							jdkMan.linkToExistingJdk(jdkPath, versionOrId);
						}
					}
				} else {
					jdk = jdkMan.getJdk(versionOrId, JdkProvider.Predicates.canInstall);
					if (jdk == null) {
						throw new IllegalArgumentException("JDK is not available for installation: " + versionOrId);
					}
					((dev.jbang.devkitman.Jdk.AvailableJdk) jdk).install();
				}
			} else {
				Util.infoMsg("JDK is already installed: " + jdk);
				Util.infoMsg("Use --force to install anyway");
			}
			return EXIT_OK;
		}

		private boolean isValidInteger(String str) {
			try {
				Integer.parseInt(str);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}

		private String generateUserId(String version) {
			return version + "-user";
		}

		private boolean isRemoteUrl(String value) {
			try {
				String scheme = new URI(value).getScheme();
				return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
			} catch (URISyntaxException e) {
				return false;
			}
		}

		private Path parseLocalPath(String value) {
			try {
				URI uri = new URI(value);
				if ("file".equalsIgnoreCase(uri.getScheme())) {
					return Paths.get(uri);
				}
			} catch (URISyntaxException | IllegalArgumentException e) {
				// Fall back to treating the value as a normal filesystem path.
			}
			return Paths.get(value);
		}

		private boolean isSupportedArchive(Path archivePath) {
			String name = archivePath.getFileName().toString().toLowerCase(Locale.ENGLISH);
			return name.endsWith(".zip") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
		}

		private void installJdkFromUrl(String jdkUrl) throws IOException {
			Path archivePath = NetUtil.downloadAndCacheFile(jdkUrl);
			installJdkFromArchive(archivePath);
		}

		private void installJdkFromArchive(Path archivePath) throws IOException {
			if (!isSupportedArchive(archivePath)) {
				throw new IllegalArgumentException(
						"The provided file must be a .zip or .tar.gz archive: " + archivePath);
			}

			Path jdkInstallDir = JBangJdkProvider.getJBangJdkDir();
			Files.createDirectories(jdkInstallDir);
			Path tempExtractedDir = Files.createTempDirectory(jdkInstallDir, "jdk-install-");
			try {
				UnpackUtils.unpackJdk(archivePath, tempExtractedDir);

				String installVersion = versionOrId;
				if (isValidInteger(versionOrId)) {
					installVersion = JavaUtil.readJavaVersionStringFromReleaseFile(tempExtractedDir)
						.orElseThrow(() -> new IllegalArgumentException(
								"Unable to determine JDK version from the 'release' file in " + tempExtractedDir));
				}

				Path installPath = jdkInstallDir.resolve(installVersion + "-unknown-jbang");
				Util.deletePath(installPath, true);
				Files.move(tempExtractedDir, installPath);
				Util.infoMsg("JDK has been installed from URL to: " + installPath);
			} catch (IOException | RuntimeException e) {
				Util.deletePath(tempExtractedDir, true);
				throw e;
			}
		}
	}

	@CommandDefinition(name = "list", aliases = {
			"l" }, description = "Lists installed JDKs.", generateHelp = true)
	public static class JdkList extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(shortName = 'a', name = "available", hasValue = false, description = "Shows versions available for installation")
		boolean available;

		@Option(shortName = 'P', name = "providers", hasValue = false, description = "Shows available providers")
		boolean listProviders;

		@Option(shortName = 'D', name = "distros", hasValue = false, description = "Shows distributions available for installation")
		boolean listDistros;

		@Option(shortName = 'd', name = "show-details", aliases = {
				"details" }, hasValue = false, description = "Shows detailed information for each JDK (only when format=text)")
		boolean details;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			dev.jbang.devkitman.Jdk defaultJdk = jdkMan.getDefaultJdk();
			String defVersion = defaultJdk != null ? defaultJdk.version() : "";
			PrintStream out = System.out;

			if (listProviders) {
				List<JdkProvider> providers = new ArrayList<>(jdkMan.providers());
				providers.sort(Comparator.comparing(JdkProvider::name));
				if (format == OutputFormat.json) {
					Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					parser.toJson(providers, out);
				} else {
					out.println("Available JDK Providers:");
					providers.forEach(p -> out.println("   " + p.name()));
				}
				return EXIT_OK;
			} else if (listDistros) {
				List<JdkDistroQuery.JdkDistro> distros = jdkMan.listDistros();
				distros.sort(Comparator.comparing(JdkDistroQuery.JdkDistro::name));
				if (format == OutputFormat.json) {
					Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					parser.toJson(distros, out);
				} else {
					out.println("Available JDK Distributions:");
					distros.forEach(d -> out.println("   " + d.name()));
				}
				return EXIT_OK;
			}

			List<JdkOut> jdkOuts;
			if (available) {
				List<dev.jbang.devkitman.Jdk.AvailableJdk> jdks = jdkMan.listAvailableJdks();
				jdkOuts = jdks.stream()
					.map(jdk -> new JdkOut(jdk.id(), jdk.version(), jdk.provider().name(),
							null, null,
							details ? jdk.equals(defaultJdk)
									: jdk.version().equals(defVersion),
							jdk.tags()))
					.collect(Collectors.toList());
			} else {
				List<dev.jbang.devkitman.Jdk.InstalledJdk> jdks = jdkMan.listInstalledJdks();
				jdkOuts = jdks.stream()
					.map(jdk -> new JdkOut(jdk.id(), jdk.version(), jdk.provider().name(),
							jdk.home(),
							jdk instanceof dev.jbang.devkitman.Jdk.LinkedJdk
									? ((dev.jbang.devkitman.Jdk.LinkedJdk) jdk).linked().id()
									: null,
							details ? jdk.equals(defaultJdk)
									: jdk.version().equals(defVersion),
							jdk.tags()))
					.collect(Collectors.toList());
			}
			if (!details) {
				// Only keep a list of unique versions
				Set<JdkOut> uniqueJdks = new TreeSet<>(Comparator.<JdkOut>comparingInt(j -> j.version).reversed());
				uniqueJdks.addAll(jdkOuts);
				jdkOuts = new ArrayList<>(uniqueJdks);
			}
			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(jdkOuts, out);
			} else {
				if (!jdkOuts.isEmpty()) {
					if (available) {
						out.println("Available JDKs:");
					} else {
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
							if (jdk.linkedId != null) {
								out.print(", link to " + jdk.linkedId);
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
	}

	@CommandDefinition(name = "uninstall", aliases = {
			"u" }, description = "Uninstalls an existing JDK.", generateHelp = true)
	public static class JdkUninstall extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(paramLabel = "versionOrId", description = "The version to uninstall", required = true)
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
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
	}

	@CommandDefinition(name = "home", description = "Prints the folder where the given JDK is installed.", generateHelp = true)
	public static class JdkHome extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(paramLabel = "versionOrId", description = "The version of the JDK to select")
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

	@CommandDefinition(name = "java-env", aliases = {
			"env" }, description = "Prints out the environment variables needed to use the given JDK.", generateHelp = true)
	public static class JdkJavaEnv extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(paramLabel = "versionOrId", description = "The version of the JDK to select")
		String versionOrId;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
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
	}

	@CommandDefinition(name = "exec", aliases = {
			"x" }, description = "Executes the given command using the default (or specified) JDK.", generateHelp = true)
	public static class JdkExec extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Option(shortName = 'j', name = "java", description = "JDK version to use for executing the command.")
		String versionOrId;

		@Arguments(paramLabel = "command", arity = "1..*", description = "Command to execute", required = true)
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
				String homeStr = Util.pathToString(home);
				String homeOsStr = Util.pathToOsString(home);
				String fullCmd = CommandBuffer.of(args).asCommandLine();
				switch (Util.getShell()) {
				case bash:
					fullCmd = "env PATH=\"" + homeStr + "/bin:$PATH\" JAVA_HOME='" + homeOsStr + "' " + fullCmd;
					break;
				case cmd:
					fullCmd = "set \"PATH=" + homeStr + "\\bin;%PATH%\" && set \"JAVA_HOME=" + homeOsStr
							+ "\" && " + fullCmd;
					break;
				case powershell:
					fullCmd = "{ $oldPath, $env:PATH, $oldHome, $env:JAVA_HOME=$env:PATH, \"" + homeStr
							+ "\\bin;$env:PATH\", $env:JAVA_HOME, '" + homeOsStr + "' ; " + fullCmd
							+ " ; $env:PATH, $env:JAVA_HOME=$oldPath, $oldHome }";
					break;
				}
				Util.verboseMsg("Executing in Java environment: " + fullCmd);
				System.out.println(fullCmd);
				return EXIT_EXECUTE;
			}
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "default", description = "Sets the default JDK to be used by JBang.", generateHelp = true)
	public static class JdkDefault extends BaseCommand {

		@Mixin
		JdkProvidersMixin jdkMixin;

		@Argument(paramLabel = "versionOrId", description = "The version of the JDK to select")
		String versionOrId;

		@Option(name = "for-version", shortName = 'v', hasValue = false, description = "Sets the default for the specified major version")
		boolean forVersion;

		@Option(shortName = 'd', name = "show-details", aliases = {
				"details" }, hasValue = false, description = "Shows detailed information for each JDK (only when format=text)")
		boolean details;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		@Override
		public Integer doCall() throws IOException {
			JdkManager jdkMan = jdkMixin.getJdkManager();
			if (!jdkMan.hasDefaultProvider()) {
				Util.warnMsg("Cannot perform operation, the 'default' provider was not found");
				return EXIT_INVALID_INPUT;
			}
			if (versionOrId != null) {
				dev.jbang.devkitman.Jdk.InstalledJdk jdk = jdkMan.getOrInstallJdk(versionOrId);
				dev.jbang.devkitman.Jdk.InstalledJdk defjdk = forVersion
						? jdkMan.getDefaultJdkForVersion(jdk.majorVersion())
						: jdkMan.getDefaultJdk();
				if (defjdk == null || (!jdk.equals(defjdk) && !Objects.equals(jdk.home(), defjdk.home()))) {
					if (forVersion) {
						jdkMan.setDefaultJdkForVersion(jdk);
					} else {
						jdkMan.setDefaultJdk(jdk);
					}
				} else {
					Util.infoMsg("Default JDK already set to " + defjdk.majorVersion());
				}
			} else {
				java.util.List<dev.jbang.devkitman.Jdk.LinkedJdk> jdks = jdkMan.listDefaultJdks();
				java.util.List<JdkOut> jdkOuts = jdks.stream()
					.map(jdk -> new JdkOut(jdk.id(), jdk.version(), jdk.provider().name(), jdk.home(),
							jdk.linked().id(), jdk.id().equals("default"), jdk.tags()))
					.collect(Collectors.toList());
				PrintStream out = System.out;
				if (format == OutputFormat.json) {
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
							out.print(jdk.linkedId);
							if (details) {
								out.print(" (" + jdk.realHomeDir + ", " + jdk.fullVersion + ", " + jdk.id);
								if (!jdk.tags.isEmpty()) {
									out.print(", " + jdk.tags);
								}
								out.print(")");
							} else {
								out.print(" (" + jdk.fullVersion + ")");
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
}
