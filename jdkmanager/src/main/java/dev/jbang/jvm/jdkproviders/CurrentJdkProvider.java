package dev.jbang.jvm.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.JdkProvider;
import dev.jbang.jvm.util.JavaUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This JDK provider returns the "current" JDK, which is the JDK that is currently being used to run
 * JBang.
 */
public class CurrentJdkProvider implements JdkProvider {
    @NonNull
    @Override
    public List<Jdk> listInstalled() {
        String jh = System.getProperty("java.home");
        if (jh != null) {
            Path jdkHome = Paths.get(jh);
            jdkHome = JavaUtils.jre2jdk(jdkHome);
            Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkHome);
            if (version.isPresent()) {
                String id = "current";
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
