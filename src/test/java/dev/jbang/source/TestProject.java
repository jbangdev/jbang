package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.WarTestFixtures;

public class TestProject extends BaseTest {

	@Test
	void testCDS() {
		Source source = new JavaSource(ResourceRef.forLiteral("//CDS\nclass m { }"), null);
		Source source2 = new JavaSource(ResourceRef.forLiteral("class m { }"), null);

		Project prj = Project.builder().build(source);
		Project prj2 = Project.builder().build(source2);

		assertTrue(prj.enableCDS());
		assertFalse(prj2.enableCDS());
	}

	@Test
	void testHasExecutableExtensionJar() {
		Path jarPath = Paths.get("test.jar");
		assertTrue(Project.hasExecutableExtension(jarPath));
	}

	@Test
	void testHasExecutableExtensionWar() {
		Path warPath = Paths.get("test.war");
		assertTrue(Project.hasExecutableExtension(warPath));
	}

	@Test
	void testHasExecutableExtensionNotArchive() {
		Path javaPath = Paths.get("test.java");
		assertFalse(Project.hasExecutableExtension(javaPath));
	}

	@Test
	void testHasExecutableExtensionNull() {
		assertFalse(Project.hasExecutableExtension(null));
	}

	@Test
	void testIsJarBackwardsCompatibility() throws IOException {
		Path jarPath = Files.createTempFile("test", ".jar");
		try {
			Project project = new Project(ResourceRef.forFile(jarPath));
			assertTrue(project.isExecutableArchive());
		} finally {
			Files.deleteIfExists(jarPath);
		}
	}

	@Test
	void testIsJarWorksForWar() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			Project project = new Project(ResourceRef.forFile(warPath));
			assertTrue(project.isExecutableArchive());
		} finally {
			Files.deleteIfExists(warPath);
		}
	}

	@Test
	void testIsExecutableArchiveJar() throws IOException {
		Path jarPath = Files.createTempFile("test", ".jar");
		try {
			Project project = new Project(ResourceRef.forFile(jarPath));
			assertTrue(project.isExecutableArchive());
		} finally {
			Files.deleteIfExists(jarPath);
		}
	}

	@Test
	void testIsExecutableArchiveWar() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			Project project = new Project(ResourceRef.forFile(warPath));
			assertTrue(project.isExecutableArchive());
		} finally {
			Files.deleteIfExists(warPath);
		}
	}

}
