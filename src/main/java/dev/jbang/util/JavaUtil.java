package dev.jbang.util;

import static java.lang.System.getenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.JdkManager;
import dev.jbang.jvm.JdkProvider;
import dev.jbang.jvm.jdkproviders.*;

public class JavaUtil {

	@Nonnull
	public static JdkManager defaultJdkManager(String... names) {
		return (new JdkManBuilder()).provider(names).build();
	}

	@Nonnull
	public static JdkManager defaultJdkManager(List<String> names) {
		return (new JdkManBuilder()).provider(names).build();
	}

	public static class JdkManBuilder extends JdkManager.Builder {
		private final List<String> providerNames = new ArrayList<>();

		public static final List<String> PROVIDERS_ALL = Collections.unmodifiableList(Arrays.asList(new String[] {
				"current", "default", "javahome", "path", "linked", "jbang", "linux", "sdkman", "scoop" }));
		public static final List<String> PROVIDERS_DEFAULT = Collections.unmodifiableList(Arrays.asList(new String[] {
				"current", "default", "javahome", "path", "linked", "jbang" }));

		public JdkManager.Builder provider(String... names) {
			return provider(names != null ? Arrays.asList(names) : null);
		}

		public JdkManager.Builder provider(List<String> names) {
			if (names != null) {
				for (String providerName : names) {
					if (PROVIDERS_ALL.contains(providerName)) {
						providerNames.add(providerName);
					}
				}
			}
			return this;
		}

		public JdkManager build() {
			if (providerNames.isEmpty() && providers.isEmpty()) {
				provider(PROVIDERS_DEFAULT);
			}
			for (String providerName : providerNames) {
				JdkProvider provider = createProvider(providerName);
				if (provider != null && provider.canUse()) {
					provider(provider);
				}
			}
			return super.build();
		}

		private JdkProvider createProvider(String providerName) {
			JdkProvider provider;
			switch (providerName) {
			case "current":
				provider = new CurrentJdkProvider();
				break;
			case "default":
				provider = new DefaultJdkProvider(Settings.getDefaultJdkDir());
				break;
			case "javahome":
				provider = new JavaHomeJdkProvider();
				break;
			case "jbang":
				provider = new FoojayJdkProvider(Settings.getCacheDir(Cache.CacheClass.jdks));
				break;
			case "linked":
				provider = new LinkedJdkProvider(Settings.getCacheDir(Cache.CacheClass.jdks));
				break;
			case "linux":
				provider = new LinuxJdkProvider();
				break;
			case "path":
				provider = new PathJdkProvider();
				break;
			case "scoop":
				provider = new ScoopJdkProvider();
				break;
			case "sdkman":
				provider = new SdkmanJdkProvider();
				break;
			default:
				Util.warnMsg("Unknown JDK provider: " + providerName);
				return null;
			}
			return provider;
		}
	}

	/**
	 * Returns the Path to JAVA_HOME
	 * 
	 * @return A Path pointing to JAVA_HOME or null if it isn't defined
	 */
	public static Path getJdkHome() {
		if (getenv("JAVA_HOME") != null) {
			return Paths.get(getenv("JAVA_HOME"));
		} else {
			return null;
		}
	}

	private static final Pattern javaVersionPattern = Pattern.compile("\"([^\"]+)\"");

	public static String parseJavaOutput(String output) {
		if (output != null) {
			Matcher m = javaVersionPattern.matcher(output);
			if (m.find() && m.groupCount() == 1) {
				return m.group(1);
			}
		}
		return null;
	}

	public static int parseJavaVersion(String version) {
		if (version != null) {
			try {
				String[] nums = version.split("[-.+]");
				String num = nums.length > 1 && nums[0].equals("1") ? nums[1] : nums[0];
				return Integer.parseInt(num);
			} catch (NumberFormatException ex) {
				// Ignore
			}
		}
		return 0;
	}

	public static boolean isOpenVersion(String version) {
		return version.endsWith("+");
	}

	public static boolean satisfiesRequestedVersion(String rv, int v) {
		if (rv == null) {
			return true;
		}
		int reqVer = minRequestedVersion(rv);
		return satisfiesRequestedVersion(reqVer, isOpenVersion(rv), v);
	}

	public static boolean satisfiesRequestedVersion(int reqVer, boolean open, int v) {
		if (reqVer <= 0) {
			return true;
		}
		if (open) {
			return v >= reqVer;
		} else {
			return v == reqVer;
		}
	}

	public static int minRequestedVersion(String rv) {
		return Integer.parseInt(isOpenVersion(rv) ? rv.substring(0, rv.length() - 1) : rv);
	}

	public static boolean checkRequestedVersion(String rv) {
		if (!isRequestedVersion(rv)) {
			throw new IllegalArgumentException(
					"Invalid JAVA version, should be a number optionally followed by a plus sign");
		}
		return true;
	}

	public static boolean isRequestedVersion(String rv) {
		return rv.matches("\\d+[+]?");
	}

	public static int getCurrentMajorJavaVersion() {
		return parseJavaVersion(System.getProperty("java.version"));
	}

	public static String resolveInJavaHome(@Nonnull String cmd, @Nonnull Jdk jdk) {
		Path jdkHome = jdk.getHome();
		if (jdkHome != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return jdkHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
		}
		return cmd;
	}

	public static Optional<Integer> resolveJavaVersionFromPath(Path home) {
		return resolveJavaVersionStringFromPath(home).map(JavaUtil::parseJavaVersion);
	}

	public static Optional<String> resolveJavaVersionStringFromPath(Path home) {
		Optional<String> res = readJavaVersionStringFromReleaseFile(home);
		if (!res.isPresent()) {
			res = readJavaVersionStringFromJavaCommand(home);
		}
		return res;
	}

	public static Optional<String> readJavaVersionStringFromReleaseFile(Path home) {
		try (Stream<String> lines = Files.lines(home.resolve("release"))) {
			return lines
						.filter(l -> l.startsWith("JAVA_VERSION=") || l.startsWith("JAVA_RUNTIME_VERSION="))
						.map(JavaUtil::parseJavaOutput)
						.findAny();
		} catch (IOException e) {
			Util.verboseMsg("Unable to read 'release' file in path: " + home);
			return Optional.empty();
		}
	}

	public static Optional<String> readJavaVersionStringFromJavaCommand(Path home) {
		Optional<String> res;
		Path javaCmd = Util.searchPath("java", home.resolve("bin").toString());
		if (javaCmd != null) {
			String output = Util.runCommand(javaCmd.toString(), "-version");
			res = Optional.ofNullable(parseJavaOutput(output));
		} else {
			res = Optional.empty();
		}
		if (!res.isPresent()) {
			Util.verboseMsg("Unable to obtain version from: '" + javaCmd + " -version'");
		}
		return res;
	}

	/**
	 * Method takes the given path which might point to a Java home directory or to
	 * the `jre` directory inside it and makes sure to return the path to the actual
	 * home directory.
	 */
	public static Path jre2jdk(Path jdkHome) {
		// Detect if the current JDK is a JRE and try to find the real home
		if (!Files.isRegularFile(jdkHome.resolve("release"))) {
			Path jh = jdkHome.toAbsolutePath();
			try {
				jh = jh.toRealPath();
			} catch (IOException e) {
				// Ignore error
			}
			if (jh.endsWith("jre") && Files.isRegularFile(jh.getParent().resolve("release"))) {
				jdkHome = jh.getParent();
			}
		}
		return jdkHome;
	}

	public static class RequestedVersionComparator implements Comparator<String> {
		@Override
		public int compare(String v1, String v2) {
			if (v1 == null && v2 == null) {
				return 0;
			}
			if (v1 == null || !isRequestedVersion(v1)) {
				return 1;
			}
			if (v2 == null || !isRequestedVersion(v2)) {
				return -1;
			}
			int n1 = minRequestedVersion(v1);
			int n2 = minRequestedVersion(v1);
			if (n1 < n2) {
				return -1;
			} else if (n1 > n2) {
				return 1;
			} else {
				boolean v1ext = v1.endsWith("+");
				boolean v2ext = v2.endsWith("+");
				if (!v1ext && v2ext) {
					return -1;
				} else if (v1ext && !v2ext) {
					return 1;
				}
			}
			return 0;
		}
	}
}
