package dev.jbang.net;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import dev.jbang.cli.ExitException;
import dev.jbang.net.jdkproviders.BaseFoldersJdkProvider;
import dev.jbang.net.jdkproviders.JBangJdkProvider;
import dev.jbang.net.jdkproviders.ScoopJdkProvider;
import dev.jbang.net.jdkproviders.SdkmanJdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JdkManager {
	private static List<JdkProvider> providers() {
		List<JdkProvider> provs = new ArrayList<>();
		provs.add(new JBangJdkProvider());
		if (SdkmanJdkProvider.canUse()) {
			provs.add(new SdkmanJdkProvider());
		}
		if (ScoopJdkProvider.canUse()) {
			provs.add(new ScoopJdkProvider());
		}
		return provs;
	}

	private static List<JdkProvider> updatableProviders() {
		return providers().stream().filter(JdkProvider::canUpdate).collect(Collectors.toList());
	}

	public static Path getCurrentJdk(String requestedVersion) {
		int currentVersion = JavaUtil.determineJavaVersion();
		int actualVersion = JavaUtil.javaVersion(requestedVersion);
		if (currentVersion == actualVersion) {
			Util.verboseMsg("System Java version matches requested version " + actualVersion);
			return JavaUtil.getJdkHome();
		} else {
			if (currentVersion == 0) {
				Util.verboseMsg("No system Java found, using JBang managed version " + actualVersion);
			} else {
				Util.verboseMsg("System Java version " + currentVersion + " incompatible, using JBang managed version "
						+ actualVersion);
			}
			return getInstalledJdk(actualVersion).home;
		}
	}

	public static JdkProvider.Jdk getInstalledJdk(int version) {
		JdkProvider.Jdk jdk = providers()	.stream()
											.map(p -> p.getJdkByVersion(version))
											.filter(Objects::nonNull)
											.findFirst()
											.orElse(null);
		if (jdk != null) {
			return jdk;
		}
		return downloadAndInstallJdk(version);
	}

	public static JdkProvider.Jdk downloadAndInstallJdk(int version) {
		List<JdkProvider.Jdk> jdks = getJdkByVersion(listAvailableJdks(), version);
		if (jdks.isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "JDK version is not available for installation: " + version);
		}
		JdkProvider.Jdk jdk = jdks.get(0).install();
		if (getDefaultJdk() == null) {
			setDefaultJdk(version);
		}
		return jdk;
	}

	public static void uninstallJdk(int version) {
		JdkProvider.Jdk jdk = providers()	.stream()
											.map(p -> p.getJdkByVersion(version))
											.filter(Objects::nonNull)
											.findFirst()
											.orElse(null);
		if (jdk != null) {
			JdkProvider.Jdk defaultJdk = getDefaultJdk();
			if (Util.isWindows()) {
				// On Windows we have to check nobody is currently using the JDK or we could
				// be causing all kinds of trouble
				try {
					Path jdkTmpDir = jdk.home.getParent().resolve(jdk.home.getFileName().toString() + ".tmp");
					Util.deletePath(jdkTmpDir, true);
					Files.move(jdk.home, jdkTmpDir);
				} catch (IOException ex) {
					Util.warnMsg("Cannot uninstall JDK " + version + ", it's being used");
					return;
				}
			}
			jdk.uninstall();
			if (defaultJdk != null && defaultJdk.getMajorVersion() == version) {
				Optional<Integer> newver = nextInstalledJdk(version);
				if (!newver.isPresent()) {
					newver = prevInstalledJdk(version);
				}
				if (newver.isPresent()) {
					setDefaultJdk(newver.get());
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

	public static Optional<Integer> nextInstalledJdk(int minVersion) {
		return listInstalledJdks()
									.stream()
									.map(JdkProvider.Jdk::getMajorVersion)
									.filter(v -> v >= minVersion)
									.min(Integer::compareTo);
	}

	public static Optional<Integer> prevInstalledJdk(int maxVersion) {
		return listInstalledJdks()
									.stream()
									.map(JdkProvider.Jdk::getMajorVersion)
									.filter(v -> v <= maxVersion)
									.max(Integer::compareTo);
	}

	public static SortedSet<JdkProvider.Jdk> listAvailableJdks() {
		return updatableProviders()	.stream()
									.flatMap(p -> p.listAvailable().stream())
									.collect(Collectors.toCollection(TreeSet::new));
	}

	public static SortedSet<JdkProvider.Jdk> listInstalledJdks() {
		return providers()	.stream()
							.flatMap(p -> p.listInstalled().stream())
							.collect(Collectors.toCollection(TreeSet::new));
	}

	public static boolean isInstalledJdk(int version) {
		return providers()	.stream()
							.map(p -> p.getJdkByVersion(version))
							.anyMatch(Objects::nonNull);
	}

	public static void setDefaultJdk(int version) {
		JdkProvider.Jdk defJdk = getDefaultJdk();
		int defVer = defJdk != null ? defJdk.getMajorVersion() : 0;
		if (!isInstalledJdk(version) || defVer != version) {
			// Call this just to make sure the JDK is installed
			JdkProvider.Jdk jdk = getInstalledJdk(version);
			// Check again if we really need to create a link because the
			// previous line might already have caused it to be created
			if (defVer != version) {
				removeDefaultJdk();
				jdk.setAsDefault();
				Util.infoMsg("Default JDK set to " + version);
			}
		}
	}

	public static JdkProvider.Jdk getDefaultJdk() {
		return providers()	.stream()
							.map(JdkProvider::getDefault)
							.filter(Objects::nonNull)
							.findFirst()
							.orElse(null);
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
