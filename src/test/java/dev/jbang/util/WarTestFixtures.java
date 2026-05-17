package dev.jbang.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Helper class for creating WAR file test fixtures
 */
public class WarTestFixtures {

	/**
	 * Creates an executable WAR file with a Main-Class manifest entry. The WAR is
	 * valid but minimal - contains only a manifest.
	 *
	 * @param targetPath where to create the WAR file
	 * @param mainClass  the Main-Class value for the manifest
	 * @return the targetPath for chaining
	 */
	public static Path createExecutableWar(Path targetPath, String mainClass) throws IOException {
		Objects.requireNonNull(targetPath, "targetPath cannot be null");
		Objects.requireNonNull(mainClass, "mainClass cannot be null");

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

		try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(targetPath), manifest)) {
			// WAR file created with just manifest - sufficient for testing jbang's
			// detection logic
			// Tests that need actual executable code should create real compiled classes
		}

		return targetPath;
	}
}
