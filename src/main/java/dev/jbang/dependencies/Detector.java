package dev.jbang.dependencies;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class Detector {

	public void detect(Properties properties, List<String> classiferWithLikes) {

		// "hack" to expose a property that works with javafx mac classifers
		String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			os = "osx";
		} else if (os.contains("win")) {
			os = "windows";
		}
		if (os.equals("osx")) {
			os = "mac";
			if ("aarch64".equals(System.getProperty("os.arch"))) {
				os = "mac-aarch64";
			}
		} else if (os.equals("windows")) {
			os = "win";
		}
		properties.setProperty("os.detected.jfxname", os);
	}

}
