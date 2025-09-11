package dev.jbang.dependencies;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseManager;
import eu.maveniverse.maven.nisse.source.osdetector.OsDetectorPropertySource;

public class Detector {
	private final NisseManager nisseManager = new SimpleNisseManager(
			Collections.singletonList(new OsDetectorPropertySource()));

	public void detect(Properties properties, List<String> classiferWithLikes) {

		NisseConfiguration configuration = SimpleNisseConfiguration.builder()
			.withSystemProperties(System.getProperties())
			.withPropertyKeyNamingStrategy(PropertyKeyNamingStrategies.prefixed("os.detected."))
			.build();

		Map<String, String> props = nisseManager.createProperties(configuration);

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
	}
}
