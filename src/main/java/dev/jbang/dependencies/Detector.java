package dev.jbang.dependencies;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import dev.jbang.util.Util;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import eu.maveniverse.maven.nisse.source.osdetector.OsDetectorPropertySource;

public class Detector {

	public Detector() {
		super();
	}

	public void detect(final Properties properties, List<String> classiferWithLikes) {

		// TODO: this is a minimal use of nisse to replace the os-maven-plugin defaults.
		// in future maybe add more from nisse but for now keep it simple.
		Properties uPropertiers = new Properties(properties);
		uPropertiers.put("nisse.compat.osDetector", "true");
		try {
			NisseConfiguration config = SimpleNisseConfiguration.builder()
				.withSystemProperties(System.getProperties())
				.withUserProperties(uPropertiers)
				.build();

			Map<String, String> props = new OsDetectorPropertySource().getProperties(config);

			properties.putAll(props);

			// "hack" to expose a property that works with javafx mac classifers
			String os = properties.getProperty("os.detected.name");
			if (os.equals("osx")) {
				os = "mac";
				if ("aarch64".equals(System.getProperty("os.arch"))) {
					os = "mac-aarch64";
				}
			} else if (os.equals("windows")) {
				os = "win";
			}
			properties.setProperty("os.detected.jfxname", os);

		} catch (IOException e) {
			Util.warnMsg("Failed to detect OS properties, continuing without them.", e);
		}
	}
}
