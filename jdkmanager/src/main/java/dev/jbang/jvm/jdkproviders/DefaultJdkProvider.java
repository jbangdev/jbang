package dev.jbang.jvm.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.JdkProvider;
import dev.jbang.jvm.util.FileUtils;
import dev.jbang.jvm.util.JavaUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This JDK provider returns the "default" JDK if it was set (using <code>jbang jdk default</code>).
 */
public class DefaultJdkProvider implements JdkProvider {
    @NonNull private final Path defaultJdkLink;

    public static final String DEFAULT_ID = "default";

    public DefaultJdkProvider(@NonNull Path defaultJdkLink) {
        this.defaultJdkLink = defaultJdkLink;
    }

    @NonNull
    @Override
    public List<Jdk> listInstalled() {
        if (Files.isDirectory(defaultJdkLink)) {
            Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(defaultJdkLink);
            if (version.isPresent()) {
                return Collections.singletonList(createJdk(DEFAULT_ID, defaultJdkLink, version.get()));
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public Jdk getJdkById(@NonNull String id) {
        if (id.equals(DEFAULT_ID)) {
            List<Jdk> l = listInstalled();
            if (!l.isEmpty()) {
                return l.get(0);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Jdk getJdkByPath(@NonNull Path jdkPath) {
        List<Jdk> installed = listInstalled();
        Jdk def = !installed.isEmpty() ? installed.get(0) : null;
        return def != null && def.getHome() != null && jdkPath.startsWith(def.getHome())
                ? def
                : null;
    }

    @Override
    public @NonNull Jdk install(@NonNull Jdk jdk) {
        Jdk defJdk = getJdkById(DEFAULT_ID);
        if (defJdk != null && defJdk.isInstalled() && !jdk.equals(defJdk)) {
            uninstall(defJdk);
        }
        FileUtils.createLink(defaultJdkLink, jdk.getHome());
        return defJdk;
    }

    @Override
    public void uninstall(@NonNull Jdk jdk) {
        FileUtils.deletePath(defaultJdkLink);
    }
}
