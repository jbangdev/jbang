package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.net.jdkproviders.CurrentJdkProvider;
import dev.jbang.net.jdkproviders.DefaultJdkProvider;
import dev.jbang.net.jdkproviders.JBangJdkProvider;
import dev.jbang.net.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.net.jdkproviders.PathJdkProvider;
import dev.jbang.net.jdkproviders.ScoopJdkProvider;
import dev.jbang.net.jdkproviders.SdkmanJdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JdkManager {
	private static List<JdkProvider> providers = null;

	// TODO Don't hard-code this list
	public static final String[] PROVIDERS_ALL = new String[] { "current", "default", "javahome", "path", "jbang",
			"sdkman", "scoop" };
	public static final String[] PROVIDERS_DEFAULT = new String[] { "current", "default", "javahome", "path", "jbang" };
	public static final String PROVIDERS_DEFAULT_STR = "current,default,javahome,path,jbang";

	public static void initProvidersByName(String... providerNames) {
		initProvidersByName(Arrays.asList(providerNames));
	}

	public static void initProvidersByName(List<String> providerNames) {
		if (providerNames.size() == 1 && "all".equals(providerNames.get(0))) {
			initProvidersByName(PROVIDERS_ALL);
			return;
		}
		// TODO Make providers know their names instead of hard-coding
		providers = new ArrayList<>();
		for (String name : providerNames) {
			JdkProvider provider;
			switch (name) {
			case "current":
				provider = new CurrentJdkProvider();
				break;
			case "default":
				provider = new DefaultJdkProvider();
				break;
			case "javahome":
				provider = new JavaHomeJdkProvider();
				break;
			case "path":
				provider = new PathJdkProvider();
				break;
			case "jbang":
				provider = new JBangJdkProvider();
				break;
			case "sdkman":
				provider = new SdkmanJdkProvider();
				break;
			case "scoop":
				provider = new ScoopJdkProvider();
				break;
			default:
				Util.warnMsg("Unknown JDK provider: " + name);
				continue;
			}
			if (provider.canUse()) {
				providers.add(provider);
			}
		}
		if (providers.isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "No providers could be initialized. Aborting.");
		}
	}

	public static void initProviders(List<JdkProvider> provs) {
		providers = provs;
		if (Util.isVerbose()) {
			Util.verboseMsg("Using JDK provider(s): " + providers	.stream()
																	.map(p -> p.getClass().getSimpleName())
																	.collect(Collectors.joining(", ")));
		}
	}

	@Nonnull
	private static List<JdkProvider> providers() {
		if (providers == null) {
			initProvidersByName(PROVIDERS_DEFAULT);
		}
		return providers;
	}

	@Nonnull
	private static List<JdkProvider> updatableProviders() {
		return providers().stream().filter(JdkProvider::canUpdate).collect(Collectors.toList());
	}

	/**
	 * This method is like <code>getJdk()</code> but will make sure that the JDK
	 * being returned is actually installed. It will perform an installation if
	 * necessary.
	 *
	 * @param versionOrId A version pattern, id or <code>null</code>
	 * @param command     The name of a command that must be available in the JDK to
	 *                    be considered as a valid result. Can be <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all or if one failed to
	 *                       install
	 */
	@Nonnull
	public static JdkProvider.Jdk getOrInstallJdk(@Nullable String versionOrId, @Nullable String command) {
		if (versionOrId != null) {
			if (JavaUtil.isRequestedVersion(versionOrId)) {
				return getOrInstallJdkByVersion(JavaUtil.minRequestedVersion(versionOrId),
						JavaUtil.isOpenVersion(versionOrId), command, false);
			} else {
				return getOrInstallJdkById(versionOrId, command, false);
			}
		} else {
			return getOrInstallJdkByVersion(0, true, command, false);
		}
	}

	/**
	 * This method is like <code>getJdkByVersion()</code> but will make sure that
	 * the JDK being returned is actually installed. It will perform an installation
	 * if necessary.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param command          The name of a command that must be available in the
	 *                         JDK to be considered as a valid result. Can be
	 *                         <code>null</code>
	 * @param updatableOnly    Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all or if one failed to
	 *                       install
	 */
	@Nonnull
	private static JdkProvider.Jdk getOrInstallJdkByVersion(int requestedVersion, boolean openVersion,
			@Nullable String command, boolean updatableOnly) {
		Util.verboseMsg("Looking for JDK: " + requestedVersion);
		JdkProvider.Jdk jdk = getJdkByVersion(requestedVersion, openVersion, command, updatableOnly);
		if (jdk == null) {
			if (requestedVersion > 0) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"No suitable JDK was found for requested version: " + requestedVersion);
			} else {
				throw new ExitException(EXIT_UNEXPECTED_STATE, "No suitable JDK was found");
			}
		}
		jdk = ensureInstalled(jdk);

		Util.verboseMsg("Using JDK: " + jdk);

		return jdk;
	}

	/**
	 * This method is like <code>getJdkByVersion()</code> but will make sure that
	 * the JDK being returned is actually installed. It will perform an installation
	 * if necessary.
	 *
	 * @param requestedId   The id of the JDK to return
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all or if one failed to
	 *                       install
	 */
	@Nonnull
	private static JdkProvider.Jdk getOrInstallJdkById(@Nonnull String requestedId, @Nullable String command,
			boolean updatableOnly) {
		Util.verboseMsg("Looking for JDK: " + requestedId);
		JdkProvider.Jdk jdk = getJdkById(requestedId, command, updatableOnly);
		if (jdk == null) {
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"No suitable JDK was found for requested id: " + requestedId);
		}
		jdk = ensureInstalled(jdk);

		Util.verboseMsg("Using JDK: " + jdk);

		return jdk;
	}

	private static JdkProvider.Jdk ensureInstalled(JdkProvider.Jdk jdk) {
		if (!jdk.isInstalled()) {
			jdk = jdk.install();
			if (getDefaultJdk() == null) {
				setDefaultJdk(jdk);
			}
		}
		return jdk;
	}

	/**
	 * Returns a <code>Jdk</code> object that matches the requested version from the
	 * list of currently installed JDKs or from the ones available for installation.
	 * The parameter is a string that either contains the actual (strict) major
	 * version of the JDK that should be returned, an open version terminated with a
	 * <code>+</code> sign to indicate that any later version is valid as well, or
	 * it is an id that will be matched against the ids of JDKs that are currently
	 * installed. If the requested version is <code>null</code> the "active" JDK
	 * will be returned, this is normally the JDK currently being used to run JBang
	 * itself. The method will return <code>null</code> if no installed or available
	 * JDK matches. NB: This method can return <code>Jdk</code> objects for JDKs
	 * that are currently _not_ installed. It will not cause any installs to be
	 * performed. See <code>getOrInstallJdk()</code> for that.
	 *
	 * @param versionOrId   A version pattern, id or <code>null</code>
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	public static JdkProvider.Jdk getJdk(@Nullable String versionOrId, @Nullable String command,
			boolean updatableOnly) {
		if (versionOrId != null) {
			if (JavaUtil.isRequestedVersion(versionOrId)) {
				return getJdkByVersion(JavaUtil.minRequestedVersion(versionOrId), JavaUtil.isOpenVersion(versionOrId),
						command, updatableOnly);
			} else {
				return getJdkById(versionOrId, command, updatableOnly);
			}
		} else {
			return getJdkByVersion(0, true, command, updatableOnly);
		}
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdkByVersion()</code> for that.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param command          The name of a command that must be available in the
	 *                         JDK to be considered as a valid result. Can be
	 *                         <code>null</code>
	 * @param updatableOnly    Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	private static JdkProvider.Jdk getJdkByVersion(int requestedVersion, boolean openVersion, @Nullable String command,
			boolean updatableOnly) {
		JdkProvider.Jdk jdk = getInstalledJdkByVersion(requestedVersion, openVersion, command, updatableOnly);
		if (jdk == null) {
			if (requestedVersion > 0 && (requestedVersion >= Settings.getDefaultJavaVersion() || !openVersion)) {
				jdk = getAvailableJdkByVersion(requestedVersion, false);
			} else {
				jdk = getJdkByVersion(Settings.getDefaultJavaVersion(), true, command, updatableOnly);
			}
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdkByVersion()</code> for that.
	 *
	 * @param requestedId   The id of the JDK to return
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	private static JdkProvider.Jdk getJdkById(@Nonnull String requestedId, @Nullable String command,
			boolean updatableOnly) {
		JdkProvider.Jdk jdk = getInstalledJdkById(requestedId, command, updatableOnly);
		if (jdk == null) {
			jdk = getAvailableJdkById(requestedId);
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version
	 * or id. Will return <code>null</code> if no JDK of that version or id is
	 * currently installed.
	 *
	 * @param versionOrId   A version pattern, id or <code>null</code>
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	public static JdkProvider.Jdk getInstalledJdk(String versionOrId, @Nullable String command, boolean updatableOnly) {
		if (versionOrId != null) {
			if (JavaUtil.isRequestedVersion(versionOrId)) {
				return getInstalledJdkByVersion(JavaUtil.minRequestedVersion(versionOrId),
						JavaUtil.isOpenVersion(versionOrId), command, updatableOnly);
			} else {
				return getInstalledJdkById(versionOrId, command, updatableOnly);
			}
		} else {
			return getInstalledJdkByVersion(0, true, command, updatableOnly);
		}
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version.
	 * Will return <code>null</code> if no JDK of that version is currently
	 * installed.
	 *
	 * @param version       The (major) version of the JDK to return
	 * @param openVersion   Return newer version if exact is not available
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	private static JdkProvider.Jdk getInstalledJdkByVersion(int version, boolean openVersion, @Nullable String command,
			boolean updatableOnly) {
		return providers()	.stream()
							.filter(p -> !updatableOnly || p.canUpdate())
							.map(p -> p.getJdkByVersion(version, openVersion))
							.filter(Objects::nonNull)
							.filter(jdk -> command == null || jdk.hasCommand(command))
							.findFirst()
							.orElse(null);
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK with the given id.
	 * Will return <code>null</code> if no JDK with that id is currently installed.
	 *
	 * @param requestedId   The id of the JDK to return
	 * @param command       The name of a command that must be available in the JDK
	 *                      to be considered as a valid result. Can be
	 *                      <code>null</code>
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	private static JdkProvider.Jdk getInstalledJdkById(String requestedId, @Nullable String command,
			boolean updatableOnly) {
		return providers()	.stream()
							.filter(p -> !updatableOnly || p.canUpdate())
							.map(p -> p.getJdkById(requestedId))
							.filter(Objects::nonNull)
							.filter(jdk -> command == null || jdk.hasCommand(command))
							.findFirst()
							.orElse(null);
	}

	@Nonnull
	private static JdkProvider.Jdk getAvailableJdkByVersion(int version, boolean openVersion) {
		List<JdkProvider.Jdk> jdks = getJdkByVersion(listAvailableJdks(), version, openVersion);
		if (jdks.isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "JDK version is not available for installation: " + version
					+ "\n"
					+ "Use 'jbang jdk list --available' to see a list of JDKs available for installation");
		}
		return jdks.get(0);
	}

	@Nonnull
	private static JdkProvider.Jdk getAvailableJdkById(String id) {
		List<JdkProvider.Jdk> jdks = getJdkById(listAvailableJdks(), id);
		if (jdks.isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "JDK id is not available for installation: " + id
					+ "\n"
					+ "Use 'jbang jdk list --available --show-details' to see a list of JDKs available for installation");
		}
		return jdks.get(0);
	}

	public static void uninstallJdk(JdkProvider.Jdk jdk) {
		JdkProvider.Jdk defaultJdk = getDefaultJdk();
		if (Util.isWindows()) {
			// On Windows we have to check nobody is currently using the JDK or we could
			// be causing all kinds of trouble
			try {
				Path jdkTmpDir = jdk.getHome()
									.getParent()
									.resolve("_delete_me_" + jdk.getHome().getFileName().toString());
				Files.move(jdk.getHome(), jdkTmpDir);
				Files.move(jdkTmpDir, jdk.getHome());
			} catch (IOException ex) {
				Util.warnMsg("Cannot uninstall JDK, it's being used: " + jdk);
				return;
			}
		}

		boolean resetDefault = false;
		if (defaultJdk != null) {
			Path defHome = defaultJdk.getHome();
			try {
				resetDefault = Files.isSameFile(defHome, jdk.getHome());
			} catch (IOException ex) {
				Util.verboseMsg("Error while trying to reset default JDK", ex);
				resetDefault = defHome.equals(jdk.getHome());
			}
		}

		jdk.uninstall();

		if (resetDefault) {
			Optional<JdkProvider.Jdk> newjdk = nextInstalledJdk(jdk.getMajorVersion(), true);
			if (!newjdk.isPresent()) {
				newjdk = prevInstalledJdk(jdk.getMajorVersion(), true);
			}
			if (newjdk.isPresent()) {
				setDefaultJdk(newjdk.get());
			} else {
				removeDefaultJdk();
				Util.infoMsg("Default JDK unset");
			}
		}
	}

	/**
	 * Links JBang JDK folder to an already existing JDK path with a link. It checks
	 * if the incoming version number is the same that the linked JDK has, if not an
	 * exception will be raised.
	 *
	 * @param path    path to the pre-installed JDK.
	 * @param version requested version to link.
	 */
	public static void linkToExistingJdk(String path, int version) {
		Path jdkPath = JBangJdkProvider.getJdksPath().resolve(Integer.toString(version));
		Util.verboseMsg("Trying to link " + path + " to " + jdkPath);
		if (Files.exists(jdkPath) || Files.isSymbolicLink(jdkPath)) {
			Util.verboseMsg("JBang managed JDK already exists, must be deleted to make sure linking works");
			Util.deletePath(jdkPath, false);
		}
		Path linkedJdkPath = Paths.get(path);
		if (!Files.isDirectory(linkedJdkPath)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to resolve path as directory: " + path);
		}
		Optional<Integer> ver = JavaUtil.resolveJavaVersionFromPath(linkedJdkPath);
		if (ver.isPresent()) {
			Integer linkedJdkVersion = ver.get();
			if (linkedJdkVersion == version) {
				Util.mkdirs(jdkPath.getParent());
				Util.createLink(jdkPath, linkedJdkPath);
				Util.infoMsg("JDK " + version + " has been linked to: " + linkedJdkPath);
			} else {
				throw new ExitException(EXIT_INVALID_INPUT, "Java version in given path: " + path
						+ " is " + linkedJdkVersion + " which does not match the requested version " + version + "");
			}
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to determine Java version in given path: " + path);
		}
	}

	/**
	 * Returns an installed JDK that matches the requested version or the next
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 *
	 * @param minVersion    the minimal version to return
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return an optional JDK
	 */
	private static Optional<JdkProvider.Jdk> nextInstalledJdk(int minVersion, boolean updatableOnly) {
		return listInstalledJdks()
									.stream()
									.filter(jdk -> !updatableOnly || jdk.getProvider().canUpdate())
									.filter(jdk -> jdk.getMajorVersion() >= minVersion)
									.min(JdkProvider.Jdk::compareTo);
	}

	/**
	 * Returns an installed JDK that matches the requested version or the previous
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 * 
	 * @param maxVersion    the maximum version to return
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return an optional JDK
	 */
	private static Optional<JdkProvider.Jdk> prevInstalledJdk(int maxVersion, boolean updatableOnly) {
		return listInstalledJdks()
									.stream()
									.filter(jdk -> !updatableOnly || jdk.getProvider().canUpdate())
									.filter(jdk -> jdk.getMajorVersion() <= maxVersion)
									.min(JdkProvider.Jdk::compareTo);
	}

	public static List<JdkProvider.Jdk> listAvailableJdks() {
		return updatableProviders()	.stream()
									.flatMap(p -> p.listAvailable().stream())
									.collect(Collectors.toList());
	}

	public static List<JdkProvider.Jdk> listInstalledJdks() {
		return providers()	.stream()
							.flatMap(p -> p.listInstalled().stream())
							.sorted()
							.collect(Collectors.toList());
	}

	@Nullable
	public static JdkProvider.Jdk getDefaultJdk() {
		return (new DefaultJdkProvider()).getJdkById("default");
	}

	public static void setDefaultJdk(JdkProvider.Jdk jdk) {
		JdkProvider.Jdk defJdk = getDefaultJdk();
		if (jdk.isInstalled() && !jdk.equals(defJdk)) {
			removeDefaultJdk();
			Util.createLink(getDefaultJdkPath(), jdk.getHome());
			Util.infoMsg("Default JDK set to " + jdk);
		}
	}

	public static void removeDefaultJdk() {
		Path link = getDefaultJdkPath();
		if (Files.isSymbolicLink(link)) {
			try {
				Files.deleteIfExists(link);
			} catch (IOException e) {
				// Ignore
			}
		} else {
			Util.deletePath(link, true);
		}
	}

	private static Path getDefaultJdkPath() {
		return Settings.getCurrentJdkDir();
	}

	public static boolean isCurrentJdkManaged() {
		Path home = JavaUtil.getJdkHome();
		return home != null && updatableProviders().stream().anyMatch(p -> p.getJdkByPath(home) != null);
	}

	private static List<JdkProvider.Jdk> getJdkByVersion(Collection<JdkProvider.Jdk> jdks, int version,
			boolean openVersion) {
		Stream<JdkProvider.Jdk> s = jdks.stream();
		if (openVersion) {
			s = s.filter(jdk -> jdk.getMajorVersion() >= version);
		} else {
			s = s.filter(jdk -> jdk.getMajorVersion() == version);
		}
		return s.collect(Collectors.toList());
	}

	private static List<JdkProvider.Jdk> getJdkById(Collection<JdkProvider.Jdk> jdks, String id) {
		return jdks.stream().filter(jdk -> jdk.getId().equals(id)).collect(Collectors.toList());
	}
}
