package dev.jbang.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;

class TestJdkManager extends BaseTest {

	@Test
	void testResolveJavaVersionFromPathWhenJavaVersionHasNewVersioning(@TempDir File javaDir) throws IOException {
		// Given
		String rawJavaVersion = "JAVA_VERSION=\"11.0.14\"";
		File release = new File(javaDir, "release");
		Files.write(release.toPath(), rawJavaVersion.getBytes());

		// When
		Optional<Integer> javaVersion = JdkManager.resolveJavaVersionFromPath(javaDir.toPath());

		// Then
		assertEquals(11, javaVersion.get());
	}

	@Test
	void testResolveJavaVersionFromPathWhenJavaVersionHasOldVersioning(@TempDir File javaDir) throws IOException {
		// Given
		String rawJavaVersion = "JAVA_VERSION=\"1.8.0_302\"";
		File release = new File(javaDir, "release");
		Files.write(release.toPath(), rawJavaVersion.getBytes());

		// When
		Optional<Integer> javaVersion = JdkManager.resolveJavaVersionFromPath(javaDir.toPath());

		// Then
		assertEquals(8, javaVersion.get());
	}

}