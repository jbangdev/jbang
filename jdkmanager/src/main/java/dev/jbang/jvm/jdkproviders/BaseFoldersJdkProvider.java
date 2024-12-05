package dev.jbang.jvm.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.JdkProvider;
import dev.jbang.jvm.util.JavaUtils;
import dev.jbang.jvm.util.OsUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public abstract class BaseFoldersJdkProvider implements JdkProvider {
    protected final Path jdksRoot;

    private static final Logger LOGGER = Logger.getLogger(BaseFoldersJdkProvider.class.getName());

    protected BaseFoldersJdkProvider(Path jdksRoot) {
        this.jdksRoot = jdksRoot;
    }

    @Override
    public boolean canUse() {
        return Files.isDirectory(jdksRoot);
    }

    @NonNull
    @Override
    public List<Jdk> listInstalled() {
        if (Files.isDirectory(jdksRoot)) {
            try (Stream<Path> jdkPaths = listJdkPaths()) {
                return jdkPaths.map(this::createJdk)
                        .filter(Objects::nonNull)
                        .sorted(Jdk::compareTo)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Couldn't list installed JDKs", e);
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public Jdk getJdkById(@NonNull String id) {
        if (isValidId(id)) {
            try (Stream<Path> jdkPaths = listJdkPaths()) {
                return jdkPaths.filter(p -> jdkId(p.getFileName().toString()).equals(id))
                        .map(this::createJdk)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Couldn't list installed JDKs", e);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Jdk getJdkByPath(@NonNull Path jdkPath) {
        if (jdkPath.startsWith(jdksRoot)) {
            try (Stream<Path> jdkPaths = listJdkPaths()) {
                return jdkPaths.filter(jdkPath::startsWith)
                        .map(this::createJdk)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Couldn't list installed JDKs", e);
            }
        }
        return null;
    }

    /**
     * Returns a path to the requested JDK. This method should never return <code>null</code> and
     * should return the path where the requested JDK is either currently installed or where it
     * would be installed if it were available. This only needs to be implemented for providers that
     * are updatable.
     *
     * @param jdk The identifier of the JDK to install
     * @return A path to the requested JDK
     */
    @NonNull
    protected Path getJdkPath(@NonNull String jdk) {
        return jdksRoot.resolve(jdk);
    }

    protected Predicate<Path> sameJdk(Path jdkRoot) {
        Path release = jdkRoot.resolve("release");
        return (Path p) -> {
            try {
                return Files.isSameFile(p.resolve("release"), release);
            } catch (IOException e) {
                return false;
            }
        };
    }

    protected Stream<Path> listJdkPaths() throws IOException {
        if (Files.isDirectory(jdksRoot)) {
            return Files.list(jdksRoot).filter(this::acceptFolder);
        }
        return Stream.empty();
    }

    @Nullable
    protected Jdk createJdk(Path home) {
        String name = home.getFileName().toString();
        Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(home);
        if (version.isPresent() && acceptFolder(home)) {
            return createJdk(jdkId(name), home, version.get());
        }
        return null;
    }

    protected boolean acceptFolder(Path jdkFolder) {
        return OsUtils.searchPath("javac", jdkFolder.resolve("bin").toString()) != null;
    }

    protected boolean isValidId(String id) {
        return id.endsWith("-" + name());
    }

    @NonNull
    protected String jdkId(String name) {
        return name + "-" + name();
    }
}
