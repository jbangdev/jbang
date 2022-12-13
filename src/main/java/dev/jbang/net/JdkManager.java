package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;
import static dev.jbang.util.JavaUtil.isOpenVersion;

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
import dev.jbang.net.jdkproviders.BaseFoldersJdkProvider;
import dev.jbang.net.jdkproviders.JBangJdkProvider;
import dev.jbang.net.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.net.jdkproviders.PathJdkProvider;
import dev.jbang.net.jdkproviders.ScoopJdkProvider;
import dev.jbang.net.jdkproviders.SdkmanJdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JdkManager {
	private static List<JdkProvider> providers = null;

	public static void initProvidersByName(String... providerNames) {
		initProvidersByName(Arrays.asList(providerNames));
	}

	public static void initProvidersByName(List<String> providerNames) {
		if (providerNames.size() == 1 && "all".equals(providerNames.get(0))) {
			// TODO Don't hard-code this list
			initProvidersByName("javahome", "path", "jbang", "sdkman", "scoop");
			return;
		}
		// TODO Make providers know their names instead of hard-coding
		providers = new ArrayList<>();
		for (String name : providerNames) {
			JdkProvider provider;
			switch (name) {
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
			initProvidersByName("javahome", "path", "jbang");
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
		JdkProvider.Jdk jdk = getJdk(requestedVersion);
		if (jdk == null) {
			if (requestedVersion != null) {
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

		if (Util.isVerbose()) {
			int actualVersion = jdk.getMajorVersion();
			int currentVersion = JavaUtil.determineJavaVersion();
			if (currentVersion == actualVersion) {
				Util.verboseMsg("System Java version matches requested version " + actualVersion);
			} else {
				if (currentVersion == 0) {
					Util.verboseMsg("No system Java found, using JBang managed version " + actualVersion);
				} else {
					Util.verboseMsg(
							"System Java version " + currentVersion + " incompatible, using JBang managed version "
									+ actualVersion);
				}
			}
		}

		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The requested version is a string that either contains the
	 * actual (strict) major version of the JDK that should be returned or an open
	 * version terminated with a <code>+</code> sign to indicate that any later
	 * version is valid as well. If the requested version is <code>null</code> the
	 * "default" JDK will be returned, this is normally the JDK currently being used
	 * to run JBang itself. The method will return <code>null</code> if no installed
	 * or available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdk()</code> for that.
	 * 
	 * @param requestedVersion A version pattern or <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws ExitException If no JDK could be found at all
	 */
	@Nullable
	public static JdkProvider.Jdk getJdk(String requestedVersion) {
		JdkProvider.Jdk jdk = getDefaultJdk();
		int defVersion = jdk != null ? jdk.getMajorVersion() : 0;
		if (requestedVersion != null) {
			if (!JavaUtil.satisfiesRequestedVersion(requestedVersion, defVersion)) {
				int minVersion = JavaUtil.minRequestedVersion(requestedVersion);
				if (isOpenVersion(requestedVersion)) {
					jdk = nextInstalledJdk(minVersion).orElseGet(() -> getJdk(minVersion));
				} else {
					jdk = getJdk(minVersion);
				}
			}
		} else {
			if (defVersion < 8) {
				jdk = getJdk(Settings.getDefaultJavaVersion() + "+");
			}
		}
		return jdk;
	}

	@Nonnull
	private static JdkProvider.Jdk getJdk(int version) {
		JdkProvider.Jdk jdk = getInstalledJdk(version, false);
		if (jdk == null) {
			jdk = getAvailableJdk(version);
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version.
	 * Will return <code>null</code> if no JDK of that version is currently
	 * installed.
	 *
	 * @param version       The (major) version of the JDK to return
	 * @param updatableOnly Determines if the result should be from an updatable
	 *                      provider
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	public static JdkProvider.Jdk getInstalledJdk(int version, boolean updatableOnly) {
		return providers()	.stream()
							.filter(p -> !updatableOnly || p.canUpdate())
							.map(p -> p.getJdkByVersion(version))
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
												.map(p -> p.getJdkByVersion(version))
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
					Path jdkTmpDir = jdk.getHome().getParent().resolve(jdk.getHome().getFileName().toString() + ".tmp");
					Util.deletePath(jdkTmpDir, true);
					Files.move(jdk.getHome(), jdkTmpDir);
				} catch (IOException ex) {
					Util.warnMsg("Cannot uninstall JDK " + version + ", it's being used");
					return;
				}
			}
			jdk.uninstall();
			if (defaultJdk != null && defaultJdk.getMajorVersion() == version) {
				Optional<JdkProvider.Jdk> newjdk = nextInstalledJdk(version);
				if (!newjdk.isPresent()) {
					newjdk = prevInstalledJdk(version);
				}
				if (newjdk.isPresent()) {
					setDefaultJdk(newjdk.get());
				} else {
					jdk.getProvider().removeDefault();
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
		Optional<Integer> ver = BaseFoldersJdkProvider.resolveJavaVersionFromPath(linkedJdkPath);
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
	 * @param minVersion the minimal version to return
	 * @return an optional JDK
	 */
	private static Optional<JdkProvider.Jdk> nextInstalledJdk(int minVersion) {
		return listInstalledJdks()
									.stream()
									.filter(jdk -> jdk.getMajorVersion() >= minVersion)
									.min(JdkProvider.Jdk::compareTo);
	}

	/**
	 * Returns an installed JDK that matches the requested version or the previous
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 * 
	 * @param maxVersion the maximum version to return
	 * @return an optional JDK
	 */
	private static Optional<JdkProvider.Jdk> prevInstalledJdk(int maxVersion) {
		return listInstalledJdks()
									.stream()
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
							.map(p -> p.getJdkByVersion(version))
							.anyMatch(Objects::nonNull);
	}

	public static JdkProvider.Jdk getDefaultJdk() {
		return streamDefaultJdks().findFirst().orElse(null);
	}

	public static JdkProvider.Jdk getUpdatableDefaultJdk() {
		return JdkManager	.streamDefaultJdks()
							.filter(j -> j.getProvider().canUpdate())
							.findFirst()
							.orElse(null);
	}

	public static Stream<JdkProvider.Jdk> streamDefaultJdks() {
		return providers()	.stream()
							.map(JdkProvider::getDefault)
							.filter(Objects::nonNull);
	}

	public static void setDefaultJdk(JdkProvider.Jdk jdk) {
		JdkProvider.Jdk defJdk = getDefaultJdk();
		if (jdk.isInstalled() && !jdk.equals(defJdk)) {
			removeDefaultJdk();
			jdk.setAsDefault();
			Util.infoMsg("Default JDK set to " + jdk);
		}
	}

	public static void removeDefaultJdk() {
		updatableProviders().forEach(JdkProvider::removeDefault);
	}

	public static boolean isCurrentJdkManaged() {
		Path home = JavaUtil.getJdkHome();
		return home != null && updatableProviders().stream().anyMatch(p -> p.getJdkByPath(home) != null);
	}

	private static List<JdkProvider.Jdk> getJdkByVersion(Collection<JdkProvider.Jdk> jdks, int version) {
		return jdks.stream().filter(jdk -> jdk.getMajorVersion() == version).collect(Collectors.toList());
	}
}
