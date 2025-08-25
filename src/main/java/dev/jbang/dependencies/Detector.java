package dev.jbang.dependencies;

import java.util.List;
import java.util.Properties;

import dev.jbang.util.Util;

public class Detector extends kr.motd.maven.os.Detector {

	public Detector() {
		super();
	}

	@Override
	protected void log(String message) {

	}

	@Override
	protected void logProperty(String name, String value) {
		Util.verboseMsg("Auto-detected: " + name + "=" + value);
	}

	public void detect(Properties properties, List<String> classiferWithLikes) {
		super.detect(properties, classiferWithLikes);

		// "hack" to expose a property that works with javafx mac classifers
		// https://repo1.maven.org/maven2/org/openjfx/javafx-controls/26-ea+3/
		// linux-aarch64
		// linux
		// mac-aarch64
		// mac
		// win
		String os = properties.getProperty("os.detected.name");
		if (os.equals("osx")) {
			os = "mac";
			if ("aarch64".equals(System.getProperty("os.arch"))) {
				os = "mac-aarch64";
			}
		} else if (os.equals("windows")) {
			os = "win";
		} else if (os.equals("linux")) {
			os = "linux";
			if ("aarch64".equals(System.getProperty("os.arch"))) {
				os = "linux-aarch64";
			}
		}
		properties.setProperty("os.detected.jfxname", os);
	}

}
