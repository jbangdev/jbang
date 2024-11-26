package dev.jbang.jvm.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.JdkProvider;
import dev.jbang.jvm.util.JavaUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This JDK provider detects if a JDK is already available on the system by looking at <code>
 * JAVA_HOME</code> environment variable.
 */
public class JavaHomeJdkProvider implements JdkProvider {
    @NonNull
    @Override
    public List<Jdk> listInstalled() {
        Path jdkHome = JavaUtils.getJavaHomeEnv();
        if (jdkHome != null && Files.isDirectory(jdkHome)) {
            Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkHome);
            if (version.isPresent()) {
                String id = "javahome";
                return Collections.singletonList(createJdk(id, jdkHome, version.get()));
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public Jdk getJdkById(@NonNull String id) {
        if (id.equals(name())) {
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
}
