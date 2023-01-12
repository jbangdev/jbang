package dev.jbang.net.jdkproviders;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.net.JdkManager;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

/**
 * JBang's main JDK provider that can download and install the JDKs provided by
 * the Foojay Disco API. They get installed in JBang's cache folder.
 */
public class JBangJdkProvider extends BaseFoldersJdkProvider {
	private static final String FOOJAY_JDK_DOWNLOAD_URL = "https://api.foojay.io/disco/v3.0/directuris?";
	private static final String FOOJAY_JDK_VERSIONS_URL = "https://api.foojay.io/disco/v3.0/distributions/%s?";

	private static class VersionsResult {
		List<String> versions;
	}

	private static class VersionsResponse {
		List<VersionsResult> result;
	}

	@Nonnull
	@Override
	public List<Jdk> listAvailable() {
		try {
			List<Jdk> result = new ArrayList<>();
			Consumer<String> addJdk = version -> {
				result.add(createJdk(jdkId(version), null, version));
			};
			String distro = Util.getVendor();
			if (distro == null) {
				VersionsResponse res = Util.readJsonFromURL(getVersionsUrl("temurin"), null, VersionsResponse.class);
				res.result.get(0).versions.forEach(addJdk);
				res = Util.readJsonFromURL(getVersionsUrl("aoj"), null, VersionsResponse.class);
				res.result.get(0).versions.forEach(addJdk);
			} else {
				VersionsResponse res = Util.readJsonFromURL(getVersionsUrl(distro), null, VersionsResponse.class);
				res.result.get(0).versions.forEach(addJdk);
			}
			result.sort(Jdk::compareTo);
			return Collections.unmodifiableList(result);
		} catch (IOException e) {
			Util.verboseMsg("Couldn't list available JDKs", e);
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public Jdk getJdkByVersion(int version, boolean openVersion) {
		Path jdk = getJdksRoot().resolve(Integer.toString(version));
		if (Files.isDirectory(jdk)) {
			return createJdk(jdk);
		} else if (openVersion) {
			return super.getJdkByVersion(version, true);
		}
		return null;
	}

	@Nonnull
	@Override
	public Jdk install(@Nonnull String jdk) {
		int version = jdkVersion(jdk);
		Util.infoMsg("Downloading JDK " + version + ". Be patient, this can take several minutes...");
		String url = getDownloadUrl(version, Util.getOS(), Util.getArch(), Util.getVendor());
		Util.verboseMsg("Downloading " + url);
		Path jdkDir = getJdkPath(jdk);
		Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".tmp");
		Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".old");
		Util.deletePath(jdkTmpDir, false);
		Util.deletePath(jdkOldDir, false);
		try {
			Path jdkPkg = Util.downloadAndCacheFile(url);
			Util.infoMsg("Installing JDK " + version + "...");
			Util.verboseMsg("Unpacking to " + jdkDir);
			UnpackUtil.unpackJdk(jdkPkg, jdkTmpDir);
			if (Files.isDirectory(jdkDir)) {
				Files.move(jdkDir, jdkOldDir);
			}
			Files.move(jdkTmpDir, jdkDir);
			Util.deletePath(jdkOldDir, false);
			Optional<String> fullVersion = JavaUtil.resolveJavaVersionStringFromPath(jdkDir);
			if (!fullVersion.isPresent()) {
				throw new ExitException(EXIT_UNEXPECTED_STATE, "Cannot obtain version of recently installed JDK");
			}
			return createJdk(jdk, jdkDir, fullVersion.get());
		} catch (Exception e) {
			Util.deletePath(jdkTmpDir, true);
			if (!Files.isDirectory(jdkDir) && Files.isDirectory(jdkOldDir)) {
				try {
					Files.move(jdkOldDir, jdkDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			String msg = "Required Java version not possible to download or install.";
			Jdk defjdk = JdkManager.getJdk(null);
			if (defjdk != null) {
				msg += " You can run with '--java " + defjdk.getMajorVersion()
						+ "' to force using the default installed Java.";
			}
			Util.errorMsg(msg);
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download or install JDK version " + version, e);
		}
	}

	@Override
	public void uninstall(@Nonnull String jdk) {
		Path jdkDir = getJdkPath(jdk);
		Util.deletePath(jdkDir, false);
	}

	@Nonnull
	@Override
	protected Path getJdkPath(@Nonnull String jdk) {
		return getJdksPath().resolve(Integer.toString(jdkVersion(jdk)));
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	private static String getDownloadUrl(int version, Util.OS os, Util.Arch arch, String distro) {
		Map<String, String> params = new HashMap<>();
		params.put("version", String.valueOf(version));

		if (distro == null) {
			if (version == 8 || version == 11 || version >= 17) {
				distro = "temurin";
			} else {
				distro = "aoj";
			}
		}
		params.put("distro", distro);

		String archiveType;
		if (os == Util.OS.windows) {
			archiveType = "zip";
		} else {
			archiveType = "tar.gz";
		}
		params.put("archive_type", archiveType);

		params.put("architecture", arch.name());
		params.put("package_type", "jdk");
		params.put("operating_system", os.name());

		if (os == Util.OS.windows) {
			params.put("libc_type", "c_std_lib");
		} else if (os == Util.OS.mac) {
			params.put("libc_type", "libc");
		} else {
			params.put("libc_type", "glibc");
		}

		params.put("javafx_bundled", "false");
		params.put("latest", "available");

		return FOOJAY_JDK_DOWNLOAD_URL + urlEncodeUTF8(params);
	}

	private static String getVersionsUrl(String distro) {
		Map<String, String> params = new HashMap<>();
		params.put("latest_per_update", "true");
		return String.format(FOOJAY_JDK_VERSIONS_URL, distro) + urlEncodeUTF8(params);
	}

	static String urlEncodeUTF8(Map<?, ?> map) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append(String.format("%s=%s",
					urlEncodeUTF8(entry.getKey().toString()),
					urlEncodeUTF8(entry.getValue().toString())));
		}
		return sb.toString();
	}

	static String urlEncodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	@Nonnull
	@Override
	protected Path getJdksRoot() {
		return getJdksPath();
	}

	public static Path getJdksPath() {
		return Settings.getCacheDir(Cache.CacheClass.jdks);
	}

	@Nonnull
	@Override
	protected String jdkId(String name) {
		int majorVersion = JavaUtil.parseJavaVersion(name);
		return majorVersion + "-jbang";
	}

	private static int jdkVersion(String jdk) {
		return JavaUtil.parseJavaVersion(jdk);
	}
}
