package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.util.WarTestFixtures;

public class TestBuildContext extends BaseTest {

	@Test
	void testGetJarFileForWar() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			Project project = Project.builder().build(warPath);
			BuildContext ctx = BuildContext.forProject(project);
			Path jarFile = ctx.getJarFile();
			assertNotNull(jarFile);
			assertEquals(warPath, jarFile);
		} finally {
			Files.deleteIfExists(warPath);
		}
	}
}
