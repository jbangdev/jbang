package dev.jbang.net;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;
import org.jetbrains.kotlin.config.KotlinCompilerVersion;
import org.jetbrains.kotlin.library.KotlinAbiVersion;
import org.jetbrains.kotlin.library.KotlinLibraryKt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

public class KotlinManager {
    private static final String KOTLIN_DOWNLOAD_URL =
        "https://github.com/JetBrains/kotlin/releases/download/v%s/kotlin-compiler-%s.zip";
    public static final String DEFAULT_KOTLIN_VERSION = KotlinCompilerVersion.VERSION;

    public static String resolveInKotlinHome(String cmd, String requestedVersion) {
        Path kotlinHome = getKotlin(requestedVersion);
        if (kotlinHome != null) {
            if (Util.isWindows()) {
                cmd = cmd + ".exe";
            }
            return kotlinHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
        }
        return cmd;
    }

    public static Path getKotlin(String requestedVersion) {
        Path kotlinPath = getKotlinPath(requestedVersion);
        if (!Files.isDirectory(kotlinPath)) {
            kotlinPath = downloadAndInstallKotlin(requestedVersion);
        }
        return kotlinPath.resolve("kotlinc");
    }

    public static Path downloadAndInstallKotlin(String version) {
        Util.infoMsg("Downloading Kotlin " + version + ". Be patient, this can take several minutes...");
        String url = String.format(KOTLIN_DOWNLOAD_URL, version, version);
        Util.verboseMsg("Downloading " + url);
        Path kotlinDir = getKotlinPath(version);
        Path kotlinTmpDir = kotlinDir.getParent().resolve(kotlinDir.getFileName().toString() + ".tmp");
        Path kotlinOldDir = kotlinDir.getParent().resolve(kotlinDir.getFileName().toString() + ".old");
        Util.deletePath(kotlinTmpDir, false);
        Util.deletePath(kotlinOldDir, false);
        try {
            Path kotlinPkg = Util.downloadAndCacheFile(url);
            Util.infoMsg("Installing Kotlin " + version + "...");
            Util.verboseMsg("Unpacking to " + kotlinDir);
            UnpackUtil.unpack(kotlinPkg, kotlinTmpDir);
            if (Files.isDirectory(kotlinDir)) {
                Files.move(kotlinDir, kotlinOldDir);
            }
            Files.move(kotlinTmpDir, kotlinDir);
            Util.deletePath(kotlinOldDir, false);
            return kotlinDir;
        } catch (Exception e) {
            Util.deletePath(kotlinTmpDir, true);
            if (!Files.isDirectory(kotlinDir) && Files.isDirectory(kotlinOldDir)) {
                try {
                    Files.move(kotlinOldDir, kotlinDir);
                } catch (IOException ex) {
                    // Ignore
                }
            }
            Util.errorMsg("Required Java version not possible to download or install. You can run with '--java "
                          + JavaUtil.determineJavaVersion() + "' to force using the default installed Java.");
            throw new ExitException(EXIT_UNEXPECTED_STATE,
                "Unable to download or install JDK version " + version, e);
        }
    }

    public static Path getKotlinPath(String version) {
        return getKotlinsPath().resolve(version);
    }

    private static Path getKotlinsPath() {
        return Settings.getCacheDir(Cache.CacheClass.kotlins);
    }

}