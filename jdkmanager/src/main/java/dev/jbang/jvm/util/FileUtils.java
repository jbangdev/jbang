package dev.jbang.jvm.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class FileUtils {
    private static final Logger LOGGER = Logger.getLogger(JavaUtils.class.getName());

    public static void createLink(Path link, Path target) {
        if (!Files.exists(link)) {
            // On Windows we use junction for directories because their
            // creation doesn't require any special privileges.
            if (OsUtils.isWindows() && Files.isDirectory(target)) {
                if (createJunction(link, target.toAbsolutePath())) {
                    return;
                }
            } else {
                if (createSymbolicLink(link, target.toAbsolutePath())) {
                    return;
                }
            }
            throw new IllegalStateException("Failed to create link " + link + " -> " + target);
        }
    }

    private static boolean createSymbolicLink(Path link, Path target) {
        try {
            mkdirs(link.getParent());
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException e) {
            if (OsUtils.isWindows()
                    && e instanceof AccessDeniedException
                    && e.getMessage().contains("privilege")) {
                LOGGER.log(
                        Level.INFO,
                        "Creation of symbolic link failed {0} -> {1}}",
                        new Object[] {link, target});
                LOGGER.info(
                        "This is a known issue with trying to create symbolic links on Windows.");
                LOGGER.info("See the information available at the link below for a solution:");
                LOGGER.info(
                        "https://www.jbang.dev/documentation/guide/latest/usage.html#usage-on-windows");
            }
            LOGGER.log(Level.FINE, "Failed to create symbolic link " + link + " -> " + target, e);
        }
        return false;
    }

    private static boolean createJunction(Path link, Path target) {
        if (!Files.exists(link) && Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            // We automatically remove broken links
            deletePath(link);
        }
        mkdirs(link.getParent());
        return OsUtils.runCommand(
                        "cmd.exe", "/c", "mklink", "/j", link.toString(), target.toString())
                != null;
    }

    /**
     * Returns true if the final part of the path is a symbolic link.
     * @param path The path to check
     * @return true if the final part of the path is a symbolic link
     */
    public static boolean isLink(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent().toRealPath();
            Path absPath = parent.resolve(path.getFileName());
            return !absPath.toRealPath().equals(absPath.toRealPath(LinkOption.NOFOLLOW_LINKS));
        } catch (IOException e) {
            return false;
        }
    }

    public static void mkdirs(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory " + p, e);
        }
    }

    public static void deletePath(Path path) {
        try {
            if (isLink(path)) {
                LOGGER.log(Level.FINE, "Deleting link {0}", path);
                Files.delete(path);
            } else if (Files.isDirectory(path)) {
                LOGGER.log(Level.FINE, "Deleting folder {0}", path);
                try (Stream<Path> s = Files.walk(path)) {
                    s.sorted(Comparator.reverseOrder())
                            .forEach(
                                    f -> {
                                        try {
                                            Files.delete(f);
                                        } catch (IOException e) {
                                            throw new IllegalStateException("Failed to delete " + f, e);
                                        }
                                    });
                }
            } else if (Files.exists(path)) {
                LOGGER.log(Level.FINE, "Deleting file {0}", path);
                Files.delete(path);
            } else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.log(Level.FINE, "Deleting broken link {0}", path);
                Files.delete(path);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete " + path, e);
        }
    }

    public static String extension(String name) {
        int p = name.lastIndexOf('.');
        return p > 0 ? name.substring(p + 1) : "";
    }
}
