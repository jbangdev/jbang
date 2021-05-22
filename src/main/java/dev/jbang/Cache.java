package dev.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.cli.ExitException;
import dev.jbang.net.JdkManager;
import dev.jbang.util.Util;

public class Cache {

	public enum CacheClass {
		urls, jars, jdks, kotlins, projects, scripts, stdins, deps
	}

	static void setupCache(Path dir) {
		// create cache dir if it does not yet exist
		dir.toFile().mkdirs();
	}

	public static void clearCache(CacheClass... classes) {
		for (CacheClass cc : classes) {
			Util.infoMsg("Clearing cache for " + cc.name());
			if (cc == CacheClass.jdks && Util.isWindows() && JdkManager.isCurrentJdkManaged()) {
				// We're running using a managed JDK on Windows so we can't just delete the
				// entire folder!
				for (Integer v : JdkManager.listInstalledJdks()) {
					JdkManager.uninstallJdk(v);
				}
			}
			if (cc == CacheClass.deps) {
				try {
					if (Settings.getCacheDependencyFile().toFile().exists()) {
						Util.verboseMsg("Deleting file " + Settings.getCacheDependencyFile());
						Files.deleteIfExists(Settings.getCacheDependencyFile().toAbsolutePath());
					}
				} catch (IOException io) {
					throw new ExitException(-1,
							"Could not delete dependency cache " + Settings.getCacheDependencyFile().toString(), io);
				}
			} else {
				Util.deletePath(Settings.getCacheDir(cc), true);
			}
		}
	}
}
