package dev.jbang.jvm.util;

import static java.lang.System.getenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JavaUtils {

    private static final Pattern javaVersionPattern = Pattern.compile("\"([^\"]+)\"");

    private static final Logger LOGGER = Logger.getLogger(JavaUtils.class.getName());

    public static boolean isRequestedVersion(String rv) {
        return rv.matches("\\d+[+]?");
    }

    public static int minRequestedVersion(String rv) {
        return Integer.parseInt(isOpenVersion(rv) ? rv.substring(0, rv.length() - 1) : rv);
    }

    public static boolean isOpenVersion(String version) {
        return version.endsWith("+");
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

    public static Optional<Integer> resolveJavaVersionFromPath(Path home) {
        return resolveJavaVersionStringFromPath(home).map(JavaUtils::parseJavaVersion);
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
            return lines.filter(
                            l ->
                                    l.startsWith("JAVA_VERSION=")
                                            || l.startsWith("JAVA_RUNTIME_VERSION="))
                    .map(JavaUtils::parseJavaOutput)
                    .findAny();
        } catch (IOException e) {
            LOGGER.fine("Unable to read 'release' file in path: " + home);
            return Optional.empty();
        }
    }

    public static Optional<String> readJavaVersionStringFromJavaCommand(Path home) {
        Optional<String> res;
        Path javaCmd = OsUtils.searchPath("java", home.resolve("bin").toString());
        if (javaCmd != null) {
            String output = OsUtils.runCommand(javaCmd.toString(), "-version");
            res = Optional.ofNullable(parseJavaOutput(output));
        } else {
            res = Optional.empty();
        }
        if (!res.isPresent()) {
            LOGGER.log(Level.FINE, "Unable to obtain version from: '{0} -version'", javaCmd);
        }
        return res;
    }

    public static String parseJavaOutput(String output) {
        if (output != null) {
            Matcher m = javaVersionPattern.matcher(output);
            if (m.find() && m.groupCount() == 1) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * Returns the Path to JAVA_HOME
     *
     * @return A Path pointing to JAVA_HOME or null if it isn't defined
     */
    public static Path getJavaHomeEnv() {
        if (getenv("JAVA_HOME") != null) {
            return Paths.get(getenv("JAVA_HOME"));
        } else {
            return null;
        }
    }

    /**
     * Method takes the given path which might point to a Java home directory or to the `jre`
     * directory inside it and makes sure to return the path to the actual home directory.
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
}
