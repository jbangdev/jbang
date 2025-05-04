package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.util.Util;

public class TestEditLocate {

	@Test
	void testLocateProjectWithNoBuildRoot(@TempDir Path root) {

		Map<String, String> files = new HashMap<>();
		files.put("src/a/b/test.java", "package a.b;\n");

		setupFiles(root, files);

		assertEquals(root.resolve("src"),
				Edit.locateProjectDir(root.resolve("src/a/b/test.java")),
				"With no build root markers it should give us current dir of the file.");
		assertEquals(root.resolve("src/a/b/"),
				Edit.locateProjectDir(root.resolve("src/a/b/")),
				"With ");
	}

	@Test
	void testLocateProjectWithAPom(@TempDir Path root) {

		Map<String, String> files = new HashMap<>();
		files.put("src/a/b/test.java", "package a.b;\n");
		files.put("pom.xml", "somexml");

		setupFiles(root, files);

		assertEquals(root, Edit.locateProjectDir(root.resolve("src/a/b/test.java")));
		assertEquals(root, Edit.locateProjectDir(root.resolve("src/a/b/")));
	}

	@Test
	void testLocateProjectWithNoPackage(@TempDir Path root) {

		Map<String, String> files = new HashMap<>();
		files.put("src/a/b/test.java", "\n");

		setupFiles(root, files);

		assertEquals(root.resolve("src/a/b"), Edit.locateProjectDir(root.resolve("src/a/b/test.java")));
	}

	@Test
	void testLocateProjectWithDotJBang(@TempDir Path root) {

		Map<String, String> files = new HashMap<>();
		files.put("src/a/b/test.java", "\n");
		files.put(".jbang/marker", "");

		setupFiles(root, files);

		assertEquals(root, Edit.locateProjectDir(root.resolve("src/a/b/test.java")));
	}

	@Test
	void testLocateProjectWithNoPom(@TempDir Path root) {

		Map<String, String> files = new HashMap<>();
		files.put("src/a/b/test.java", "package a.b;\n");
		// files.put("pom.xml", "somexml");

		setupFiles(root, files);

		assertEquals(root.resolve("src"), Edit.locateProjectDir(root.resolve("src/a/b/test.java")));
	}

	private static void setupFiles(Path root, Map<String, String> files) {
		files.forEach((name, content) -> {
			try {

				Path path = Paths.get(name);
				if (content == null) {
					Files.createDirectory(root.resolve(path));
				} else {
					root.resolve(path).toFile().getParentFile().mkdirs();
					Util.writeString(root.resolve(path), content);
				}
			} catch (IOException ie) {
				throw new RuntimeException(ie);
			}
		});
	}

}
