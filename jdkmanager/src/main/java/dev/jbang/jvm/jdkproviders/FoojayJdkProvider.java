package dev.jbang.jvm.jdkproviders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.util.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * JVM's main JDK provider that can download and install the JDKs provided by the Foojay Disco API.
 * They get installed in JBang's cache folder.
 */
public class FoojayJdkProvider extends BaseFoldersJdkProvider {
    private static final String FOOJAY_JDK_DOWNLOAD_URL =
            "https://api.foojay.io/disco/v3.0/directuris?";
    private static final String FOOJAY_JDK_VERSIONS_URL =
            "https://api.foojay.io/disco/v3.0/packages?";

    private static final Logger LOGGER = Logger.getLogger(FoojayJdkProvider.class.getName());

    private static class JdkResult {
        String java_version;
        int major_version;
        String release_status;
    }

    private static class VersionsResponse {
        List<JdkResult> result;
    }

    public FoojayJdkProvider(Path jdksPath) {
        super(jdksPath);
    }

    @NonNull
    @Override
    public List<Jdk> listAvailable() {
        try {
            List<Jdk> result = new ArrayList<>();
            Consumer<String> addJdk =
                    version -> {
                        result.add(createJdk(jdkId(version), null, version));
                    };
            String distro = getVendor();
            if (distro == null) {
                VersionsResponse res =
                        NetUtils.readJsonFromUrl(
                                getVersionsUrl(OsUtils.getOS(), OsUtils.getArch(), "temurin"),
                                VersionsResponse.class);
                filterEA(res.result).forEach(jdk -> addJdk.accept(jdk.java_version));
                res =
                        NetUtils.readJsonFromUrl(
                                getVersionsUrl(OsUtils.getOS(), OsUtils.getArch(), "aoj"),
                                VersionsResponse.class);
                filterEA(res.result).forEach(jdk -> addJdk.accept(jdk.java_version));
            } else {
                VersionsResponse res =
                        NetUtils.readJsonFromUrl(
                                getVersionsUrl(OsUtils.getOS(), OsUtils.getArch(), distro),
                                VersionsResponse.class);
                filterEA(res.result).forEach(jdk -> addJdk.accept(jdk.java_version));
            }
            result.sort(Jdk::compareTo);
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Couldn't list available JDKs", e);
        }
        return Collections.emptyList();
    }

    // Filter out any EA releases for which a GA with
    // the same major version exists
    private List<JdkResult> filterEA(List<JdkResult> jdks) {
        Set<Integer> GAs =
                jdks.stream()
                        .filter(jdk -> jdk.release_status.equals("ga"))
                        .map(jdk -> jdk.major_version)
                        .collect(Collectors.toSet());

        JdkResult[] lastJdk = new JdkResult[] {null};
        return jdks.stream()
                .filter(
                        jdk -> {
                            if (lastJdk[0] == null
                                    || lastJdk[0].major_version != jdk.major_version
                                            && (jdk.release_status.equals("ga")
                                                    || !GAs.contains(jdk.major_version))) {
                                lastJdk[0] = jdk;
                                return true;
                            } else {
                                return false;
                            }
                        })
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public Jdk getJdkByVersion(int version, boolean openVersion) {
        Path jdk = jdksRoot.resolve(Integer.toString(version));
        if (Files.isDirectory(jdk)) {
            return createJdk(jdk);
        } else if (openVersion) {
            return super.getJdkByVersion(version, true);
        }
        return null;
    }

    @NonNull
    @Override
    public Jdk install(@NonNull Jdk jdk) {
        int version = jdkVersion(jdk.getId());
        LOGGER.log(
                Level.INFO,
                "Downloading JDK {0}. Be patient, this can take several minutes...",
                version);
        String url = getDownloadUrl(version, OsUtils.getOS(), OsUtils.getArch(), getVendor());
        LOGGER.log(Level.FINE, "Downloading {0}", url);
        Path jdkDir = getJdkPath(jdk.getId());
        Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".tmp");
        Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".old");
        FileUtils.deletePath(jdkTmpDir);
        FileUtils.deletePath(jdkOldDir);
        try {
            Path jdkPkg = NetUtils.downloadFromUrl(url);
            LOGGER.log(Level.INFO, "Installing JDK {0}...", version);
            LOGGER.log(Level.FINE, "Unpacking to {0}", jdkDir);
            UnpackUtils.unpackJdk(jdkPkg, jdkTmpDir);
            if (Files.isDirectory(jdkDir)) {
                Files.move(jdkDir, jdkOldDir);
            } else if (Files.isSymbolicLink(jdkDir)) {
                // This means we have a broken/invalid link
                FileUtils.deletePath(jdkDir);
            }
            Files.move(jdkTmpDir, jdkDir);
            FileUtils.deletePath(jdkOldDir);
            Optional<String> fullVersion = JavaUtils.resolveJavaVersionStringFromPath(jdkDir);
            if (!fullVersion.isPresent()) {
                throw new IllegalStateException("Cannot obtain version of recently installed JDK");
            }
            return createJdk(jdk.getId(), jdkDir, fullVersion.get());
        } catch (Exception e) {
            FileUtils.deletePath(jdkTmpDir);
            if (!Files.isDirectory(jdkDir) && Files.isDirectory(jdkOldDir)) {
                try {
                    Files.move(jdkOldDir, jdkDir);
                } catch (IOException ex) {
                    // Ignore
                }
            }
            String msg = "Required Java version not possible to download or install.";
            /*
            Jdk defjdk = JdkManager.getJdk(null, false);
            if (defjdk != null) {
                msg +=
                        " You can run with '--java "
                                + defjdk.getMajorVersion()
                                + "' to force using the default installed Java.";
            }
            */
            LOGGER.log(Level.FINE, msg);
            throw new IllegalStateException(
                    "Unable to download or install JDK version " + version, e);
        }
    }

    @Override
    public void uninstall(@NonNull Jdk jdk) {
        Path jdkDir = getJdkPath(jdk.getId());
        FileUtils.deletePath(jdkDir);
    }

    @NonNull
    @Override
    protected Path getJdkPath(@NonNull String jdk) {
        return getJdksPath().resolve(Integer.toString(jdkVersion(jdk)));
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    private static String getDownloadUrl(
            int version, OsUtils.OS os, OsUtils.Arch arch, String distro) {
        return FOOJAY_JDK_DOWNLOAD_URL + getUrlParams(version, os, arch, distro);
    }

    private static String getVersionsUrl(OsUtils.OS os, OsUtils.Arch arch, String distro) {
        return FOOJAY_JDK_VERSIONS_URL + getUrlParams(null, os, arch, distro);
    }

    private static String getUrlParams(
            Integer version, OsUtils.OS os, OsUtils.Arch arch, String distro) {
        Map<String, String> params = new HashMap<>();
        if (version != null) {
            params.put("version", String.valueOf(version));
        }

        if (distro == null) {
            if (version == null || version == 8 || version == 11 || version >= 17) {
                distro = "temurin";
            } else {
                distro = "aoj";
            }
        }
        params.put("distro", distro);

        String archiveType;
        if (os == OsUtils.OS.windows) {
            archiveType = "zip";
        } else {
            archiveType = "tar.gz";
        }
        params.put("archive_type", archiveType);

        params.put("architecture", arch.name());
        params.put("package_type", "jdk");
        params.put("operating_system", os.name());

        if (os == OsUtils.OS.windows) {
            params.put("libc_type", "c_std_lib");
        } else if (os == OsUtils.OS.mac) {
            params.put("libc_type", "libc");
        } else {
            params.put("libc_type", "glibc");
        }

        params.put("javafx_bundled", "false");
        params.put("latest", "available");
        params.put("release_status", "ga,ea");
        params.put("directly_downloadable", "true");

        return urlEncodeUTF8(params);
    }

    static String urlEncodeUTF8(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(
                    String.format(
                            "%s=%s",
                            urlEncodeUTF8(entry.getKey().toString()),
                            urlEncodeUTF8(entry.getValue().toString())));
        }
        return sb.toString();
    }

    static String urlEncodeUTF8(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @NonNull
    public Path getJdksPath() {
        return jdksRoot;
    }

    @NonNull
    @Override
    protected String jdkId(String name) {
        int majorVersion = JavaUtils.parseJavaVersion(name);
        return majorVersion + "-jbang";
    }

    private static int jdkVersion(String jdk) {
        return JavaUtils.parseJavaVersion(jdk);
    }

    // TODO refactor
    private static String getVendor() {
        return null;
    }
}
