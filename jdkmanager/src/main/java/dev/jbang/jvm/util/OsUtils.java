package dev.jbang.jvm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OsUtils {

    private static final Logger LOGGER = Logger.getLogger(OsUtils.class.getName());

    public enum OS {
        linux,
        mac,
        windows,
        aix,
        unknown
    }

    public enum Arch {
        x32,
        x64,
        aarch64,
        arm,
        arm64,
        ppc64,
        ppc64le,
        s390x,
        riscv64,
        unknown
    }

    public static OS getOS() {
        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ENGLISH)
                        .replaceAll("[^a-z0-9]+", "");
        if (os.startsWith("mac") || os.startsWith("osx")) {
            return OS.mac;
        } else if (os.startsWith("linux")) {
            return OS.linux;
        } else if (os.startsWith("win")) {
            return OS.windows;
        } else if (os.startsWith("aix")) {
            return OS.aix;
        } else {
            LOGGER.log(Level.FINE, "Unknown OS: {0}", os);
            return OS.unknown;
        }
    }

    public static Arch getArch() {
        String arch =
                System.getProperty("os.arch")
                        .toLowerCase(Locale.ENGLISH)
                        .replaceAll("[^a-z0-9]+", "");
        if (arch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return Arch.x64;
        } else if (arch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return Arch.x32;
        } else if (arch.matches("^(aarch64)$")) {
            return Arch.aarch64;
        } else if (arch.matches("^(arm)$")) {
            return Arch.arm;
        } else if (arch.matches("^(ppc64)$")) {
            return Arch.ppc64;
        } else if (arch.matches("^(ppc64le)$")) {
            return Arch.ppc64le;
        } else if (arch.matches("^(s390x)$")) {
            return Arch.s390x;
        } else if (arch.matches("^(arm64)$")) {
            return Arch.arm64;
        } else if (arch.matches("^(riscv64)$")) {
            return Arch.riscv64;
        } else {
            LOGGER.log(Level.FINE, "Unknown Arch: {0}", arch);
            return Arch.unknown;
        }
    }

    public static boolean isWindows() {
        return getOS() == OS.windows;
    }

    public static boolean isMac() {
        return getOS() == OS.mac;
    }

    /**
     * Searches the locations defined by PATH for the given executable
     *
     * @param cmd The name of the executable to look for
     * @return A Path to the executable, if found, null otherwise
     */
    public static Path searchPath(String cmd) {
        String envPath = System.getenv("PATH");
        envPath = envPath != null ? envPath : "";
        return searchPath(cmd, envPath);
    }

    /**
     * Searches the locations defined by `paths` for the given executable
     *
     * @param cmd The name of the executable to look for
     * @param paths A string containing the paths to search
     * @return A Path to the executable, if found, null otherwise
     */
    public static Path searchPath(String cmd, String paths) {
        return Arrays.stream(paths.split(File.pathSeparator))
                .map(dir -> Paths.get(dir).resolve(cmd))
                .flatMap(OsUtils::executables)
                .filter(OsUtils::isExecutable)
                .findFirst()
                .orElse(null);
    }

    private static Stream<Path> executables(Path base) {
        if (isWindows()) {
            return Stream.of(
                    Paths.get(base.toString() + ".exe"),
                    Paths.get(base.toString() + ".bat"),
                    Paths.get(base.toString() + ".cmd"),
                    Paths.get(base.toString() + ".ps1"));
        } else {
            return Stream.of(base);
        }
    }

    private static boolean isExecutable(Path file) {
        if (Files.isRegularFile(file)) {
            if (isWindows()) {
                String nm = file.getFileName().toString().toLowerCase();
                return nm.endsWith(".exe")
                        || nm.endsWith(".bat")
                        || nm.endsWith(".cmd")
                        || nm.endsWith(".ps1");
            } else {
                return Files.isExecutable(file);
            }
        }
        return false;
    }

    /**
     * Runs the given command + arguments and returns its output (both stdout and stderr) as a
     * string
     *
     * @param cmd The command to execute
     * @return The output of the command or null if anything went wrong
     */
    public static String runCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String cmdOutput = br.lines().collect(Collectors.joining("\n"));
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                return cmdOutput;
            } else {
                LOGGER.log(
                        Level.FINE,
                        "Command failed: #{0} - {1}",
                        new Object[] {exitCode, cmdOutput});
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.FINE, "Error running: " + String.join(" ", cmd), ex);
        }
        return null;
    }
}
