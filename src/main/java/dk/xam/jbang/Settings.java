package dk.xam.jbang;

import java.io.File;

public class Settings {

	final public static File JBANG_CACHE_DIR;
	final public static File DEP_LOOKUP_CACHE_FILE;
	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	static {
		var v = System.getenv("JBANG_CACHE_DIR");

		if (v != null) {
			JBANG_CACHE_DIR = new File(v);
		} else {
			JBANG_CACHE_DIR = new File(System.getProperty("user.home"), ".jbang");
		}

		DEP_LOOKUP_CACHE_FILE = new java.io.File(JBANG_CACHE_DIR, "dependency_cache.txt");

		// create cache dir if it does not yet exist
		if (!JBANG_CACHE_DIR.isDirectory()) {
			JBANG_CACHE_DIR.mkdir();
		}

	}

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault("JBANG_REPO",
				System.getProperty("user.home") + "/.m2/repository"));
	}
}
