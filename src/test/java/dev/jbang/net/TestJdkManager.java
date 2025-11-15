package dev.jbang.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

class TestJdkManager extends BaseTest {

	@Test
	void testResolveJavaVersionFromPathWhenJavaVersionHasNewVersioning(@TempDir File javaDir) throws IOException {
		// Given
		String rawJavaVersion = "JAVA_VERSION=\"11.0.14\"";
		File release = new File(javaDir, "release");
		Util.writeString(release.toPath(), rawJavaVersion);

		// When
		Optional<Integer> javaVersion = JavaUtil.resolveJavaVersionFromPath(javaDir.toPath());

		// Then
		assertEquals(11, javaVersion.get());
	}

	@Test
	void testResolveJavaVersionFromPathWhenJavaVersionHasOldVersioning(@TempDir File javaDir) throws IOException {
		// Given
		String rawJavaVersion = "JAVA_VERSION=\"1.8.0_302\"";
		File release = new File(javaDir, "release");
		Util.writeString(release.toPath(), rawJavaVersion);

		// When
		Optional<Integer> javaVersion = JavaUtil.resolveJavaVersionFromPath(javaDir.toPath());

		// Then
		assertEquals(8, javaVersion.get());
	}

	@Test
	void testResolveJavaVersionFromPathWhenReleaseFileHasNoProperJavaVersion(@TempDir File javaDir) throws IOException {
		// Given
		String rawJavaVersion = "EMPTY";
		File release = new File(javaDir, "release");
		Util.writeString(release.toPath(), rawJavaVersion);

		// When
		Optional<Integer> javaVersion = JavaUtil.resolveJavaVersionFromPath(javaDir.toPath());

		// Then
		assertFalse(javaVersion.isPresent());
	}

	@Test
	void testResolveJavaVersionFromPathWhenReleaseFileDoesNotExist(@TempDir File javaDir) throws IOException {
		// Given
		File release = new File(javaDir, "not-release");

		// When
		Optional<Integer> javaVersion = JavaUtil.resolveJavaVersionFromPath(release.toPath());

		// Then
		assertFalse(javaVersion.isPresent());
	}

}