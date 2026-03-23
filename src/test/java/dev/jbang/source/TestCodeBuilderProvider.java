package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.util.WarTestFixtures;

public class TestCodeBuilderProvider extends BaseTest {

	@Test
	void testGetBuilderForWarWithNativeImage() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			Project project = Project.builder().nativeImage(true).build(warPath);
			BuildContext ctx = BuildContext.forProject(project);
			CodeBuilderProvider provider = CodeBuilderProvider.create(ctx);
			Builder<CmdGeneratorBuilder> builder = provider.get();
			assertNotNull(builder);
		} finally {
			Files.deleteIfExists(warPath);
		}
	}
}
