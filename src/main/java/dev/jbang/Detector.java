package dev.jbang;

import java.util.List;
import java.util.Properties;

public class Detector extends kr.motd.maven.os.Detector {

	public Detector() {
		super();
	}

	@Override
	protected void log(String message) {

	}

	@Override
	protected void logProperty(String name, String value) {

	}

	public void detect(Properties properties, List<String> classiferWithLikes) {
		super.detect(properties, classiferWithLikes);

		// "hack" to expose a property that works with javafx mac classifers
		String os = properties.getProperty("os.detected.name");
		if (os.equals("osx")) {
			os = "mac";
		}
		properties.setProperty("os.detected.jfxname", os);
		System.setProperty("os.detected.jfxname", os);
	}

}
