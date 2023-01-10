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
	 * @param requestedVersion A version pattern or <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all or if one failed to
	 *                       install
	 */
	@Nonnull
	public static JdkProvider.Jdk getOrInstallJdk(String requestedVersion) {
		if (requestedVersion != null) {
			return getOrInstallJdk(JavaUtil.minRequestedVersion(requestedVersion),
					JavaUtil.isOpenVersion(requestedVersion), false);
		} else {
			return getOrInstallJdk(0, true, false);
		}
	}

	/**
	 * This method is like <code>getJdk()</code> but will make sure that the JDK
	 * being returned is actually installed. It will perform an installation if
	 * necessary.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param updatableOnly    Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all or if one failed to
	 *                       install
	 */
	@Nonnull
	public static JdkProvider.Jdk getOrInstallJdk(int requestedVersion, boolean openVersion, boolean updatableOnly) {
		JdkProvider.Jdk jdk = getJdk(requestedVersion, openVersion, updatableOnly);
		if (jdk == null) {
			if (requestedVersion > 0) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"No suitable JDK was found for requested version: " + requestedVersion);
			} else {
				throw new ExitException(EXIT_UNEXPECTED_STATE, "No suitable JDK was found");
			}
		}
		if (!jdk.isInstalled()) {
			jdk = jdk.install();
			if (getDefaultJdk() == null) {
				setDefaultJdk(jdk);
			}
		}

		Util.verboseMsg("Using JDK: " + jdk);

		return jdk;
	}

	/**
	 * Returns a <code>Jdk</code> object that matches the requested version from the
	 * list of currently installed JDKs or from the ones available for installation.
	 * The requested version is a string that either contains the actual (strict)
	 * major version of the JDK that should be returned or an open version
	 * terminated with a <code>+</code> sign to indicate that any later version is
	 * valid as well. If the requested version is <code>null</code> the "active" JDK
	 * will be returned, this is normally the JDK currently being used to run JBang
	 * itself. The method will return <code>null</code> if no installed or available
	 * JDK matches. NB: This method can return <code>Jdk</code> objects for JDKs
	 * that are currently _not_ installed. It will not cause any installs to be
	 * performed. See <code>getOrInstallJdk()</code> for that.
	 *
	 * @param requestedVersion A version pattern or <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	public static JdkProvider.Jdk getJdk(@Nullable String requestedVersion) {
		if (requestedVersion != null) {
			return getJdk(JavaUtil.minRequestedVersion(requestedVersion), JavaUtil.isOpenVersion(requestedVersion),
					false);
		} else {
			return getJdk(0, true, false);
		}
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The requested version is a string that either contains the
	 * actual (strict) major version of the JDK that should be returned or an open
	 * version terminated with a <code>+</code> sign to indicate that any later
	 * version is valid as well. If the requested version is <code>null</code> the
	 * "active" JDK will be returned, this is normally the JDK currently being used
	 * to run JBang itself. The method will return <code>null</code> if no installed
	 * or available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdk()</code> for that.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param updatableOnly    Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	public static JdkProvider.Jdk getJdk(int requestedVersion, boolean openVersion, boolean updatableOnly) {
		JdkProvider.Jdk jdk = getInstalledJdk(requestedVersion, openVersion, updatableOnly);
		if (jdk == null) {
			if (requestedVersion > 0) {
				jdk = getAvailableJdk(requestedVersion);
			} else {
				jdk = getJdk(Settings.getDefaultJavaVersion(), true, updatableOnly);
			}
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version.
	 * Will return <code>null</code> if no JDK of that version is currently
	 * installed.
	 *
	 * @param version       The (major) version of the JDK to return
	 * @param openVersion   Return newer version if exact is not available
	 * @param updatableOnly Only return JDKs from updatable providers or not
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	public static JdkProvider.Jdk getInstalledJdk(int version, boolean openVersion, boolean updatableOnly) {
		return providers()	.stream()
							.filter(p -> !updatableOnly || p.canUpdate())
							.map(p -> p.getJdkByVersion(version, openVersion))
							.filter(Objects::nonNull)
							.findFirst()
							.orElse(null);
	}

	@Nonnull
	public static JdkProvider.Jdk downloadAndInstallJdk(int version) {
		JdkProvider.Jdk jdk = getAvailableJdk(version).install();
		if (getDefaultJdk() == null) {
			setDefaultJdk(jdk);
		}
		return jdk;
	}

	@Nonnull
	private static JdkProvider.Jdk getAvailableJdk(int version) {
		List<JdkProvider.Jdk> jdks = getJdkByVersion(listAvailableJdks(), version);
		if (jdks.isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "JDK version is not available for installation: " + version);
		}
		return jdks.get(0);
	}

	public static void uninstallJdk(int version) {
		List<JdkProvider.Jdk> jdks = providers().stream()
												.map(p -> p.getJdkByVersion(version, false))
												.filter(Objects::nonNull)
												.collect(Collectors.toList());
		JdkProvider.Jdk jdk;
		if (jdks.size() > 1) {
			// Get the first JDK from a provider that can update, or just the first
			jdk = jdks.stream().filter(j -> j.getProvider().canUpdate()).findFirst().orElse(jdks.get(0));
		} else if (jdks.size() == 1) {
			jdk = jdks.get(0);
		} else {
			jdk = null;
		}
		if (jdk != null) {
			JdkProvider.Jdk defaultJdk = getDefaultJdk();
			if (Util.isWindows()) {
				// On Windows we have to check nobody is currently using the JDK or we could
				// be causing all kinds of trouble
				try {
					Path jdkTmpDir = jdk.getHome()
										.getParent()
										.resolve("_delete_me_" + jdk.getHome().getFileName().toString());
					Files.move(jdk.getHome(), jdkTmpDir);
					Util.deletePath(jdkTmpDir, true);
				} catch (IOException ex) {
					Util.warnMsg("Cannot uninstall JDK " + version + ", it's being used");
					return;
				}
			}
			jdk.uninstall();
			if (defaultJdk != null && defaultJdk.getMajorVersion() == version) {
				Optional<JdkProvider.Jdk> newjdk = nextInstalledJdk(version, true);
				if (!newjdk.isPresent()) {
					newjdk = prevInstalledJdk(version, true);
				}
				if (newjdk.isPresent()) {
					setDefaultJdk(newjdk.get());
				} else {
					removeDefaultJdk();
					Util.infoMsg("Default JDK unset");
				}
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
		if (Files.exists(jdkPath)) {
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

	public static boolean isInstalledJdk(int version) {
		return providers()	.stream()
							.map(p -> p.getJdkByVersion(version, false))
							.anyMatch(Objects::nonNull);
	}

	public static JdkProvider.Jdk getDefaultJdk() {
		try {
			Path home = getDefaultJdkPath();
			if (Files.exists(home)) {
				Path rhome = home.toRealPath();
				return providers()	.stream()
									.map(p -> p.getJdkByPath(rhome))
									.filter(Objects::nonNull)
									.findFirst()
									.orElse(JdkProvider.UnknownJdkProvider.createJdk(rhome));
			}
		} catch (IOException ex) {
			// Ignore
			Util.errorMsg("Unable to obtain default JDK", ex);
		}
		return null;
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

	private static List<JdkProvider.Jdk> getJdkByVersion(Collection<JdkProvider.Jdk> jdks, int version) {
		return jdks.stream().filter(jdk -> jdk.getMajorVersion() == version).collect(Collectors.toList());
	}
}
