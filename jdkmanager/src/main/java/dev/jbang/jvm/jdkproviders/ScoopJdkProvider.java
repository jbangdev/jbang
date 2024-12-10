package dev.jbang.jvm.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.util.OsUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This JDK provider detects any JDKs that have been installed using the Scoop package manager.
 * Windows only.
 */
public class ScoopJdkProvider extends BaseFoldersJdkProvider {
    private static final Path SCOOP_APPS =
            Paths.get(System.getProperty("user.home")).resolve("scoop/apps");

    public ScoopJdkProvider() {
        super(SCOOP_APPS);
    }

    @NonNull
    @Override
    protected Stream<Path> listJdkPaths() throws IOException {
        if (Files.isDirectory(jdksRoot)) {
            try (Stream<Path> paths = Files.list(jdksRoot)) {
                return paths.filter(p -> p.getFileName().startsWith("openjdk"))
                        .map(p -> p.resolve("current"));
            }
        }
        return Stream.empty();
    }

    @Nullable
    @Override
    protected Jdk createJdk(Path home) {
        try {
            // Try to resolve any links
            home = home.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't resolve 'current' link: " + home, e);
        }
        return super.createJdk(home);
    }

    @Override
    public boolean canUse() {
        return OsUtils.isWindows() && super.canUse();
    }
}
