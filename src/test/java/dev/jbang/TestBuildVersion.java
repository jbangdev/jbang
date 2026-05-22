package dev.jbang;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import dev.jbang.util.BuildConfig;

public class TestBuildVersion {

	@Test
	void versionIsNotUnspecified() {
		assertNotNull(BuildConfig.VERSION, "BuildConfig.VERSION should not be null");
		assertFalse(BuildConfig.VERSION.isEmpty(), "BuildConfig.VERSION should not be empty");
		assertFalse(BuildConfig.VERSION.equals("unspecified"),
				"BuildConfig.VERSION should not be 'unspecified'");
		assertTrue(BuildConfig.VERSION.matches("\\d+\\.\\d+\\.\\d+.*"),
				"BuildConfig.VERSION should look like a semver version, but was: " + BuildConfig.VERSION);
	}

	@Test
	void manifestContainsVersion() throws IOException {
		java.nio.file.Path manifestPath = java.nio.file.Paths.get("build/tmp/jar/MANIFEST.MF");
		assumeTrue(java.nio.file.Files.exists(manifestPath),
				"Manifest file not yet generated - run jar task first");
		try (InputStream is = java.nio.file.Files.newInputStream(manifestPath)) {
			Manifest manifest = new Manifest(is);
			String jbangVersion = manifest.getMainAttributes().getValue("JBang-Version");
			assertNotNull(jbangVersion, "JBang-Version should be present in META-INF/MANIFEST.MF");
			assertFalse(jbangVersion.equals("unspecified"),
					"JBang-Version in MANIFEST.MF should not be 'unspecified'");
			assertEquals(BuildConfig.VERSION, jbangVersion,
					"JBang-Version in MANIFEST.MF should match BuildConfig.VERSION");
		}
	}
}
