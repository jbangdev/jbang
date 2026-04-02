package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

class TestWarFixtures {
	@Test
	void testCreateExecutableWar() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			Path result = WarTestFixtures.createExecutableWar(warPath, "TestMain");
			assertTrue(Files.exists(result));
			assertTrue(Files.size(result) > 0);

			// Validate manifest content
			try (JarFile jar = new JarFile(result.toFile())) {
				Manifest manifest = jar.getManifest();
				assertNotNull(manifest, "Manifest should not be null");
				assertEquals("TestMain", manifest.getMainAttributes().getValue("Main-Class"),
						"Main-Class should be set to TestMain");
				assertNotNull(manifest.getMainAttributes().getValue("Manifest-Version"),
						"Manifest-Version should be set");
			}
		} finally {
			Files.deleteIfExists(warPath);
		}
	}

	@Test
	void testCreateExecutableWarWithNullTargetPath() {
		assertThrows(NullPointerException.class, () -> {
			WarTestFixtures.createExecutableWar(null, "TestMain");
		}, "Should throw NullPointerException when targetPath is null");
	}

	@Test
	void testCreateExecutableWarWithNullMainClass() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			assertThrows(NullPointerException.class, () -> {
				WarTestFixtures.createExecutableWar(warPath, null);
			}, "Should throw NullPointerException when mainClass is null");
		} finally {
			Files.deleteIfExists(warPath);
		}
	}
}
