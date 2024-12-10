package dev.jbang.jvm.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** This JDK provider detects any JDKs that have been installed using the SDKMAN package manager. */
public class SdkmanJdkProvider extends BaseFoldersJdkProvider {
    private static final Path JDKS_ROOT =
            Paths.get(System.getProperty("user.home")).resolve(".sdkman/candidates/java");

    public SdkmanJdkProvider() {
        super(JDKS_ROOT);
    }
}
