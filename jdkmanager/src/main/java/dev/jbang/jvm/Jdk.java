package dev.jbang.jvm;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import dev.jbang.jvm.util.JavaUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Jdk extends Comparable<Jdk> {
    @NonNull JdkProvider getProvider();

    @NonNull String getId();

    @NonNull String getVersion();

    @Nullable Path getHome();

    int getMajorVersion();

    @NonNull Jdk install();

    void uninstall();

    boolean isInstalled();

    class Default implements Jdk {
        @NonNull private final transient JdkProvider provider;
        @NonNull private final String id;
        @NonNull private final String version;
        @Nullable private final Path home;
        @NonNull private final Set<String> tags = new HashSet<>();

        Default(
                @NonNull JdkProvider provider,
                @NonNull String id,
                @Nullable Path home,
                @NonNull String version,
                @NonNull String... tags) {
            this.provider = provider;
            this.id = id;
            this.version = version;
            this.home = home;
        }

        @Override
        @NonNull
        public JdkProvider getProvider() {
            return provider;
        }

        /** Returns the id that is used to uniquely identify this JDK across all providers */
        @Override
        @NonNull
        public String getId() {
            return id;
        }

        /** Returns the JDK's version */
        @Override
        @NonNull
        public String getVersion() {
            return version;
        }

        /**
         * The path to where the JDK is installed. Can be <code>null</code> which means the JDK
         * isn't currently installed by that provider
         */
        @Override
        @Nullable
        public Path getHome() {
            return home;
        }

        @Override
        public int getMajorVersion() {
            return JavaUtils.parseJavaVersion(getVersion());
        }

        @Override
        @NonNull
        public Jdk install() {
            return provider.install(this);
        }

        @Override
        public void uninstall() {
            provider.uninstall(this);
        }

        @Override
        public boolean isInstalled() {
            return home != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Default jdk = (Default) o;
            return id.equals(jdk.id) && Objects.equals(home, jdk.home);
        }

        @Override
        public int hashCode() {
            return Objects.hash(home, id);
        }

        @Override
        public int compareTo(Jdk o) {
            return Integer.compare(getMajorVersion(), o.getMajorVersion());
        }

        @Override
        public String toString() {
            return getMajorVersion() + " (" + version + ", " + id + ", " + home + ")";
        }
    }
}
