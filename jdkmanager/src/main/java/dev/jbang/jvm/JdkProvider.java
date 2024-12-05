package dev.jbang.jvm;

import java.nio.file.Path;
import java.util.*;

import dev.jbang.jvm.util.JavaUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This interface must be implemented by providers that are able to give access to JDKs installed on
 * the user's system. Some providers will also be able to manage those JDKs by installing and
 * uninstalling them at the user's request. In those cases the <code>canUpdate()</code> should
 * return <code>true</code>.
 *
 * <p>The providers deal in JDK identifiers, not in versions. Those identifiers are specific to the
 * implementation but should follow two important rules: 1. they must be unique across
 * implementations 2. they must start with an integer specifying the main JDK version
 */
public interface JdkProvider {

    default Jdk createJdk(@NonNull String id, @Nullable Path home, @NonNull String version) {
        return new Jdk.Default(this, id, home, version);
    }

    default String name() {
        String nm = getClass().getSimpleName();
        if (nm.endsWith("JdkProvider")) {
            return nm.substring(0, nm.length() - 11).toLowerCase();
        } else {
            return nm.toLowerCase();
        }
    }

    /**
     * For providers that can update this returns a set of JDKs that are available for installation.
     * Providers might set the <code>home</code> field of the JDK objects if the respective JDK is
     * currently installed on the user's system, but only if they can ensure that it's the exact
     * same version, otherwise they should just leave the field <code>null</code>.
     *
     * @return List of <code>Jdk</code> objects
     */
    @NonNull
    default List<Jdk> listAvailable() {
        throw new UnsupportedOperationException(
                "Listing available JDKs is not supported by " + getClass().getName());
    }

    /**
     * Returns a set of JDKs that are currently installed on the user's system.
     *
     * @return List of <code>Jdk</code> objects, possibly empty
     */
    @NonNull List<Jdk> listInstalled();

    /**
     * Determines if a JDK of the requested version is currently installed by this provider and if
     * so returns its respective <code>Jdk</code> object, otherwise it returns <code>null</code>. If
     * <code>openVersion</code> is set to true the method will also return the next installed
     * version if the exact version was not found.
     *
     * @param version The specific JDK version to return
     * @param openVersion Return newer version if exact is not available
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable
    default Jdk getJdkByVersion(int version, boolean openVersion) {
        List<Jdk> jdks = listInstalled();
        Jdk res;
        if (openVersion) {
            res =
                    jdks.stream()
                            .sorted()
                            .filter(jdk -> jdk.getMajorVersion() >= version)
                            .findFirst()
                            .orElse(null);
        } else {
            res =
                    jdks.stream()
                            .filter(jdk -> jdk.getMajorVersion() == version)
                            .findFirst()
                            .orElse(null);
        }
        return res;
    }

    /**
     * Determines if the given id refers to a JDK managed by this provider and if so returns its
     * respective <code>Jdk</code> object, otherwise it returns <code>null</code>.
     *
     * @param id The id to look for
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable Jdk getJdkById(@NonNull String id);

    /**
     * Determines if the given path belongs to a JDK managed by this provider and if so returns its
     * respective <code>Jdk</code> object, otherwise it returns <code>null</code>.
     *
     * @param jdkPath The path to look for
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable Jdk getJdkByPath(@NonNull Path jdkPath);

    /**
     * For providers that can update this installs the indicated JDK
     *
     * @param jdk The <code>Jdk</code> object of the JDK to install
     * @return A <code>Jdk</code> object
     * @throws UnsupportedOperationException if the provider can not update
     */
    @NonNull
    default Jdk install(@NonNull Jdk jdk) {
        throw new UnsupportedOperationException(
                "Installing a JDK is not supported by " + getClass().getName());
    }

    /**
     * Uninstalls the indicated JDK
     *
     * @param jdk The <code>Jdk</code> object of the JDK to uninstall
     * @throws UnsupportedOperationException if the provider can not update
     */
    default void uninstall(@NonNull Jdk jdk) {
        throw new UnsupportedOperationException(
                "Uninstalling a JDK is not supported by " + getClass().getName());
    }

    /**
     * Indicates if the provider can be used or not. This can perform sanity checks like the
     * availability of certain package being installed on the system or even if the system is
     * running a supported operating system.
     *
     * @return True if the provider can be used, false otherwise
     */
    default boolean canUse() {
        return true;
    }

    /**
     * Indicates if the provider is able to (un)install JDKs or not
     *
     * @return True if JDKs can be (un)installed, false otherwise
     */
    default boolean canUpdate() {
        return false;
    }

    /**
     * This is a special "dummy" provider that can be used to create <code>Jdk</code> objects for
     * JDKs that don't seem to belong to any of the known providers but for which we still want an
     * object to represent them.
     */
    class UnknownJdkProvider implements JdkProvider {
        private static final UnknownJdkProvider instance = new UnknownJdkProvider();

        @NonNull
        @Override
        public List<Jdk> listInstalled() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public Jdk getJdkById(@NonNull String id) {
            return null;
        }

        @Nullable
        @Override
        public Jdk getJdkByPath(@NonNull Path jdkPath) {
            Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkPath);
            if (version.isPresent()) {
                return createJdk("unknown", jdkPath, version.get());
            } else {
                return null;
            }
        }

        public static Jdk createJdk(Path jdkPath) {
            return instance.getJdkByPath(jdkPath);
        }
    }
}
