package dk.xam.jbang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class Settings {

	final private static File JBANG_CACHE_DIR;
	final private static File DEP_LOOKUP_CACHE_FILE;
	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	static {
		String v = System.getenv("JBANG_CACHE_DIR");

		if (v != null) {
			JBANG_CACHE_DIR = new File(v);
		} else {
			JBANG_CACHE_DIR = new File(System.getProperty("user.home"), ".jbang");
		}

		DEP_LOOKUP_CACHE_FILE = new java.io.File(JBANG_CACHE_DIR, "dependency_cache.txt");

		setupCache();

	}

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault("JBANG_REPO", System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static File getCacheDependencyFile() {
		setupCache();
		return DEP_LOOKUP_CACHE_FILE;
	}

	public static File getCacheDir(boolean init) {
		if (init)
			setupCache();
		return JBANG_CACHE_DIR;
	}

	public static File getCacheDir() {
		return getCacheDir(true);
	}

	public static void setupCache() {
		// create cache dir if it does not yet exist
		if (!JBANG_CACHE_DIR.exists()) {
			JBANG_CACHE_DIR.mkdir();
		}
	}

	public static void clearCache() {
		try {
			Files	.walk(Settings.getCacheDir().toPath())
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		} catch (IOException e) {
			throw new ExitException(-1, "Could not delete cache.", e);
		}
	}
}
