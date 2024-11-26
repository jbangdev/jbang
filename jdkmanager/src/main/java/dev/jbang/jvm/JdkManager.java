package dev.jbang.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.jvm.jdkproviders.*;
import dev.jbang.jvm.util.FileUtils;
import dev.jbang.jvm.util.JavaUtils;
import dev.jbang.jvm.util.OsUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class JdkManager {
    private static final Logger LOGGER = Logger.getLogger(JdkManager.class.getName());

    private final List<JdkProvider> providers;
    private final int defaultJavaVersion;

    private final JdkProvider defaultProvider;

    public static class Builder {
        protected final List<JdkProvider> providers = new ArrayList<>();
        protected int defaultJavaVersion = 0;

        protected Builder() {}

        public Builder provider(JdkProvider... provs) {
            providers.addAll(Arrays.asList(provs));
            return this;
        }

        public Builder defaultJavaVersion(int defaultJavaVersion) {
            this.defaultJavaVersion = defaultJavaVersion;
            return this;
        }

        public JdkManager build() {
            if (providers.isEmpty()) {
                throw new IllegalStateException("No providers could be initialized. Aborting.");
            }
            return new JdkManager(providers, defaultJavaVersion);
        }
    }

    private JdkManager(List<JdkProvider> providers, int defaultJavaVersion) {
        this.providers = Collections.unmodifiableList(providers);
        this.defaultJavaVersion = defaultJavaVersion;
        this.defaultProvider = provider("default");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(
                    "Using JDK provider(s): "
                            + providers.stream()
                                    .map(p -> p.getClass().getSimpleName())
                                    .collect(Collectors.joining(", ")));
        }
    }

    @NonNull
    private List<JdkProvider> providers() {
        return providers;
    }

    @NonNull
    private List<JdkProvider> updatableProviders() {
        return providers().stream().filter(JdkProvider::canUpdate).collect(Collectors.toList());
    }

    @Nullable
    private JdkProvider provider(String name) {
        return providers().stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * This method is like <code>getJdk()</code> but will make sure that the JDK being returned is
     * actually installed. It will perform an installation if necessary.
     *
     * @param versionOrId A version pattern, id or <code>null</code>
     * @return A <code>Jdk</code> object
     * @throws IllegalArgumentException If no JDK could be found at all or if one failed to install
     */
    @NonNull
    public Jdk getOrInstallJdk(String versionOrId) {
        if (versionOrId != null) {
            if (JavaUtils.isRequestedVersion(versionOrId)) {
                return getOrInstallJdkByVersion(
                        JavaUtils.minRequestedVersion(versionOrId),
                        JavaUtils.isOpenVersion(versionOrId),
                        false);
            } else {
                return getOrInstallJdkById(versionOrId, false);
            }
        } else {
            return getOrInstallJdkByVersion(0, true, false);
        }
    }

    /**
     * This method is like <code>getJdkByVersion()</code> but will make sure that the JDK being
     * returned is actually installed. It will perform an installation if necessary.
     *
     * @param requestedVersion The (minimal) version to return, can be 0
     * @param openVersion Return newer version if exact is not available
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     * @throws IllegalArgumentException If no JDK could be found at all or if one failed to install
     */
    @NonNull
    private Jdk getOrInstallJdkByVersion(
            int requestedVersion, boolean openVersion, boolean updatableOnly) {
        LOGGER.log(Level.FINE, "Looking for JDK: {0}", requestedVersion);
        Jdk jdk = getJdkByVersion(requestedVersion, openVersion, updatableOnly);
        if (jdk == null) {
            if (requestedVersion > 0) {
                throw new IllegalArgumentException(
                        "No suitable JDK was found for requested version: " + requestedVersion);
            } else {
                throw new IllegalArgumentException("No suitable JDK was found");
            }
        }
        jdk = ensureInstalled(jdk);
        LOGGER.log(Level.FINE, "Using JDK: {0}", jdk);

        return jdk;
    }

    /**
     * This method is like <code>getJdkByVersion()</code> but will make sure that the JDK being
     * returned is actually installed. It will perform an installation if necessary.
     *
     * @param requestedId The id of the JDK to return
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     * @throws IllegalArgumentException If no JDK could be found at all or if one failed to install
     */
    @NonNull
    private Jdk getOrInstallJdkById(@NonNull String requestedId, boolean updatableOnly) {
        LOGGER.log(Level.FINE, "Looking for JDK: {0}", requestedId);
        Jdk jdk = getJdkById(requestedId, updatableOnly);
        if (jdk == null) {
            throw new IllegalArgumentException(
                    "No suitable JDK was found for requested id: " + requestedId);
        }
        jdk = ensureInstalled(jdk);
        LOGGER.log(Level.FINE, "Using JDK: {0}", jdk);

        return jdk;
    }

    private Jdk ensureInstalled(Jdk jdk) {
        if (!jdk.isInstalled()) {
            jdk = jdk.install();
            if (getDefaultJdk() == null) {
                setDefaultJdk(jdk);
            }
        }
        return jdk;
    }

    /**
     * Returns a <code>Jdk</code> object that matches the requested version from the list of
     * currently installed JDKs or from the ones available for installation. The parameter is a
     * string that either contains the actual (strict) major version of the JDK that should be
     * returned, an open version terminated with a <code>+</code> sign to indicate that any later
     * version is valid as well, or it is an id that will be matched against the ids of JDKs that
     * are currently installed. If the requested version is <code>null</code> the "active" JDK will
     * be returned, this is normally the JDK currently being used to run JBang itself. The method
     * will return <code>null</code> if no installed or available JDK matches. NB: This method can
     * return <code>Jdk</code> objects for JDKs that are currently _not_ installed. It will not
     * cause any installs to be performed. See <code>getOrInstallJdk()</code> for that.
     *
     * @param versionOrId A version pattern, id or <code>null</code>
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     * @throws IllegalArgumentException If no JDK could be found at all
     */
    @Nullable
    public Jdk getJdk(@Nullable String versionOrId, boolean updatableOnly) {
        if (versionOrId != null) {
            if (JavaUtils.isRequestedVersion(versionOrId)) {
                return getJdkByVersion(
                        JavaUtils.minRequestedVersion(versionOrId),
                        JavaUtils.isOpenVersion(versionOrId),
                        updatableOnly);
            } else {
                return getJdkById(versionOrId, updatableOnly);
            }
        } else {
            return getJdkByVersion(0, true, updatableOnly);
        }
    }

    /**
     * Returns an <code>Jdk</code> object that matches the requested version from the list of
     * currently installed JDKs or from the ones available for installation. The method will return
     * <code>null</code> if no installed or available JDK matches. NB: This method can return <code>
     * Jdk</code> objects for JDKs that are currently _not_ installed. It will not cause any
     * installs to be performed. See <code>getOrInstallJdkByVersion()</code> for that.
     *
     * @param requestedVersion The (minimal) version to return, can be 0
     * @param openVersion Return newer version if exact is not available
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     * @throws IllegalArgumentException If no JDK could be found at all
     */
    @Nullable
    private Jdk getJdkByVersion(int requestedVersion, boolean openVersion, boolean updatableOnly) {
        Jdk jdk = getInstalledJdkByVersion(requestedVersion, openVersion, updatableOnly);
        if (jdk == null) {
            if (requestedVersion > 0
                    && (requestedVersion >= defaultJavaVersion || !openVersion)) {
                jdk = getAvailableJdkByVersion(requestedVersion, false);
            } else {
                jdk = getJdkByVersion(defaultJavaVersion, true, updatableOnly);
            }
        }
        return jdk;
    }

    /**
     * Returns an <code>Jdk</code> object that matches the requested version from the list of
     * currently installed JDKs or from the ones available for installation. The method will return
     * <code>null</code> if no installed or available JDK matches. NB: This method can return <code>
     * Jdk</code> objects for JDKs that are currently _not_ installed. It will not cause any
     * installs to be performed. See <code>getOrInstallJdkByVersion()</code> for that.
     *
     * @param requestedId The id of the JDK to return
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     * @throws IllegalArgumentException If no JDK could be found at all
     */
    @Nullable
    private Jdk getJdkById(@NonNull String requestedId, boolean updatableOnly) {
        Jdk jdk = getInstalledJdkById(requestedId, updatableOnly);
        if (jdk == null) {
            jdk = getAvailableJdkById(requestedId);
        }
        return jdk;
    }

    /**
     * Returns an <code>Jdk</code> object for an installed JDK of the given version or id. Will
     * return <code>null</code> if no JDK of that version or id is currently installed.
     *
     * @param versionOrId A version pattern, id or <code>null</code>
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable
    public Jdk getInstalledJdk(String versionOrId, boolean updatableOnly) {
        if (versionOrId != null) {
            if (JavaUtils.isRequestedVersion(versionOrId)) {
                return getInstalledJdkByVersion(
                        JavaUtils.minRequestedVersion(versionOrId),
                        JavaUtils.isOpenVersion(versionOrId),
                        updatableOnly);
            } else {
                return getInstalledJdkById(versionOrId, updatableOnly);
            }
        } else {
            return getInstalledJdkByVersion(0, true, updatableOnly);
        }
    }

    /**
     * Returns an <code>Jdk</code> object for an installed JDK of the given version. Will return
     * <code>null</code> if no JDK of that version is currently installed.
     *
     * @param version The (major) version of the JDK to return
     * @param openVersion Return newer version if exact is not available
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable
    private Jdk getInstalledJdkByVersion(int version, boolean openVersion, boolean updatableOnly) {
        return providers().stream()
                .filter(p -> !updatableOnly || p.canUpdate())
                .map(p -> p.getJdkByVersion(version, openVersion))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns an <code>Jdk</code> object for an installed JDK with the given id. Will return <code>
     * null</code> if no JDK with that id is currently installed.
     *
     * @param requestedId The id of the JDK to return
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return A <code>Jdk</code> object or <code>null</code>
     */
    @Nullable
    private Jdk getInstalledJdkById(String requestedId, boolean updatableOnly) {
        return providers().stream()
                .filter(p -> !updatableOnly || p.canUpdate())
                .map(p -> p.getJdkById(requestedId))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @NonNull
    private Jdk getAvailableJdkByVersion(int version, boolean openVersion) {
        List<Jdk> jdks = getJdkByVersion(listAvailableJdks(), version, openVersion);
        if (jdks.isEmpty()) {
            throw new IllegalArgumentException(
                    "JDK version is not available for installation: "
                            + version
                            + "\n"
                            + "Use 'jbang jdk list --available' to see a list of JDKs available for installation");
        }
        return jdks.get(0);
    }

    @NonNull
    private Jdk getAvailableJdkById(String id) {
        List<Jdk> jdks = getJdkById(listAvailableJdks(), id);
        if (jdks.isEmpty()) {
            throw new IllegalArgumentException(
                    "JDK id is not available for installation: "
                            + id
                            + "\n"
                            + "Use 'jbang jdk list --available --show-details' to see a list of JDKs available for installation");
        }
        return jdks.get(0);
    }

    public void uninstallJdk(Jdk jdk) {
        Jdk defaultJdk = getDefaultJdk();
        if (OsUtils.isWindows()) {
            // On Windows we have to check nobody is currently using the JDK or we could
            // be causing all kinds of trouble
            try {
                Path jdkTmpDir =
                        jdk.getHome()
                                .getParent()
                                .resolve("_delete_me_" + jdk.getHome().getFileName().toString());
                Files.move(jdk.getHome(), jdkTmpDir);
                Files.move(jdkTmpDir, jdk.getHome());
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Cannot uninstall JDK, it's being used: {0}", jdk);
                return;
            }
        }

        boolean resetDefault = false;
        if (defaultJdk != null) {
            Path defHome = defaultJdk.getHome();
            try {
                resetDefault = Files.isSameFile(defHome, jdk.getHome());
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Error while trying to reset default JDK", ex);
                resetDefault = defHome.equals(jdk.getHome());
            }
        }

        jdk.uninstall();

        if (resetDefault) {
            Optional<Jdk> newjdk = nextInstalledJdk(jdk.getMajorVersion(), true);
            if (!newjdk.isPresent()) {
                newjdk = prevInstalledJdk(jdk.getMajorVersion(), true);
            }
            if (newjdk.isPresent()) {
                setDefaultJdk(newjdk.get());
            } else {
                removeDefaultJdk();
                LOGGER.log(Level.INFO, "Default JDK unset");
            }
        }
    }

    /**
     * Links JBang JDK folder to an already existing JDK path with a link. It checks if the incoming
     * version number is the same that the linked JDK has, if not an exception will be raised.
     *
     * @param path path to the pre-installed JDK.
     * @param version requested version to link.
     */
    public void linkToExistingJdk(String path, int version) {
        //TODO RE-IMPLEMENT THIS! We should use a jdk/defaults folder or something for this
        JdkProvider linked = provider("linked");
        if (linked == null) {
            return;
        }
        Jdk linkedJdk = linked.getJdkById(Integer.toString(version) + "-linked");
        Path jdkPath = linkedJdk.getHome(); // FoojayJdkProvider.getJdksPath().resolve(Integer.toString(version));
        LOGGER.log(Level.FINE, "Linking JDK: {0} to {1}", new Object[] {path, jdkPath});
        if (Files.exists(jdkPath) || FileUtils.isLink(jdkPath)) {
            LOGGER.log(
                    Level.FINE,
                    "A managed JDK already exists, it must be deleted to make sure linking works");
            FileUtils.deletePath(jdkPath);
        }
        Path linkedJdkPath = Paths.get(path);
        if (!Files.isDirectory(linkedJdkPath)) {
            throw new IllegalArgumentException("Unable to resolve path as directory: " + path);
        }
        Optional<Integer> ver = JavaUtils.resolveJavaVersionFromPath(linkedJdkPath);
        if (ver.isPresent()) {
            Integer linkedJdkVersion = ver.get();
            if (linkedJdkVersion == version) {
                FileUtils.mkdirs(jdkPath.getParent());
                FileUtils.createLink(jdkPath, linkedJdkPath);
                LOGGER.log(
                        Level.INFO,
                        "JDK {0} has been linked to: {1}",
                        new Object[] {version, linkedJdkPath});
            } else {
                throw new IllegalArgumentException(
                        "Java version in given path: "
                                + path
                                + " is "
                                + linkedJdkVersion
                                + " which does not match the requested version "
                                + version);
            }
        } else {
            throw new IllegalArgumentException(
                    "Unable to determine Java version in given path: " + path);
        }
    }

    /**
     * Returns an installed JDK that matches the requested version or the next available version.
     * Returns <code>Optional.empty()</code> if no matching JDK was found;
     *
     * @param minVersion the minimal version to return
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return an optional JDK
     */
    private Optional<Jdk> nextInstalledJdk(int minVersion, boolean updatableOnly) {
        return listInstalledJdks().stream()
                .filter(jdk -> !updatableOnly || jdk.getProvider().canUpdate())
                .filter(jdk -> jdk.getMajorVersion() >= minVersion)
                .min(Jdk::compareTo);
    }

    /**
     * Returns an installed JDK that matches the requested version or the previous available
     * version. Returns <code>Optional.empty()</code> if no matching JDK was found;
     *
     * @param maxVersion the maximum version to return
     * @param updatableOnly Only return JDKs from updatable providers or not
     * @return an optional JDK
     */
    private Optional<Jdk> prevInstalledJdk(int maxVersion, boolean updatableOnly) {
        return listInstalledJdks().stream()
                .filter(jdk -> !updatableOnly || jdk.getProvider().canUpdate())
                .filter(jdk -> jdk.getMajorVersion() <= maxVersion)
                .min(Jdk::compareTo);
    }

    public List<Jdk> listAvailableJdks() {
        return updatableProviders().stream()
                .flatMap(p -> p.listAvailable().stream())
                .collect(Collectors.toList());
    }

    public List<Jdk> listInstalledJdks() {
        return providers().stream()
                .flatMap(p -> p.listInstalled().stream())
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean hasDefaultProvider() {
        return defaultProvider != null;
    }

    @Nullable
    public Jdk getDefaultJdk() {
        return hasDefaultProvider() ?
                defaultProvider.getJdkById(DefaultJdkProvider.DEFAULT_ID) : null;
    }

    public void setDefaultJdk(Jdk jdk) {
        if (hasDefaultProvider()) {
            Jdk defJdk = getDefaultJdk();
            // Check if the new jdk exists and isn't the same as the current default
            if (jdk.isInstalled() && !jdk.equals(defJdk)) {
                // Special syntax for "installing" the default JDK
                Jdk newDefJdk = defaultProvider.createJdk(DefaultJdkProvider.DEFAULT_ID, jdk.getHome(), jdk.getVersion());
                defaultProvider.install(newDefJdk);
                LOGGER.log(Level.INFO, "Default JDK set to {0}", jdk);
            }
        }
    }

    public void removeDefaultJdk() {
        Jdk defJdk = getDefaultJdk();
        if (defJdk != null) {
            defJdk.uninstall();
        }
    }

    public boolean isCurrentJdkManaged() {
        Path currentJdk = Paths.get(System.getProperty("java.home"));
        return updatableProviders().stream().anyMatch(p -> p.getJdkByPath(currentJdk) != null);
    }

    private static List<Jdk> getJdkByVersion(
            Collection<Jdk> jdks, int version, boolean openVersion) {
        Stream<Jdk> s = jdks.stream();
        if (openVersion) {
            s = s.filter(jdk -> jdk.getMajorVersion() >= version);
        } else {
            s = s.filter(jdk -> jdk.getMajorVersion() == version);
        }
        return s.collect(Collectors.toList());
    }

    private static List<Jdk> getJdkById(Collection<Jdk> jdks, String id) {
        return jdks.stream().filter(jdk -> jdk.getId().equals(id)).collect(Collectors.toList());
    }
}
