package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.util.WarTestFixtures;

public class TestAppBuilder extends BaseTest {

	@Test
	void testWarSkipsCompilation() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			Project project = Project.builder().build(warPath);
			BuildContext ctx = BuildContext.forProject(project);
			assertTrue(project.isExecutableArchive());
			assertNotNull(ctx.getJarFile());
		} finally {
			Files.deleteIfExists(warPath);
		}
	}
}
