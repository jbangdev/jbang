package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;

/**
 * Tests how <code>//PROPS</code> are scoped when files include other files
 * using <code>//SOURCES</code>.
 */
public class TestPropsDirectiveProject extends BaseTest {

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes());
	}

	private Path setupFiles(Path dir) throws IOException {
		write(dir.resolve("Main.java"), "//PROPS base=lib common=1\n"
				+ "//SOURCES ${base}/A.java ${base}/C.java\n"
				+ "//DEPS org.example:main:${common}\n"
				+ "public class Main { public static void main(String... args) {} }\n");
		write(dir.resolve("lib/A.java"), "//PROPS common=2 froma=3\n"
				+ "//SOURCES B.java\n"
				+ "//DEPS org.example:a:${common}\n"
				+ "//DEPS org.example:a-only:${froma}\n"
				+ "class A {}\n");
		write(dir.resolve("lib/B.java"), "//PROPS fromb=4\n"
				+ "//DEPS org.example:b:${common}\n"
				+ "//DEPS org.example:b-froma:${froma}\n"
				+ "//DEPS org.example:b-fromb:${fromb}\n"
				+ "class B {}\n");
		write(dir.resolve("lib/C.java"), "//DEPS org.example:c-froma:${froma:none}\n"
				+ "//DEPS org.example:c-fromb:${fromb:none}\n"
				+ "class C {}\n");
		return dir.resolve("Main.java");
	}

	@Test
	void testPropsAreScopedToIncludedFiles(@TempDir Path dir) throws Exception {
		Path main = setupFiles(dir);
		CaptureResult<Project> result = captureOutput(() -> Project.builder().build(main));
		assertThat(result.err, not(containsString("[WARN]")));
		Project prj = result.result;
		assertThat(prj.getMainSourceSet().getDependencies(), containsInAnyOrder(
				// Main's own definitions
				"org.example:main:1",
				// the including file (Main) overrides A's value for common
				"org.example:a:1",
				"org.example:a-only:3",
				// B inherits from both Main and A
				"org.example:b:1",
				"org.example:b-froma:3",
				"org.example:b-fromb:4",
				// C is a sibling of A, so it doesn't see A's or B's properties
				"org.example:c-froma:none",
				"org.example:c-fromb:none"));
	}

	@Test
	void testCommandLinePropertiesOverridePropsEverywhere(@TempDir Path dir) throws IOException {
		Path main = setupFiles(dir);
		Project prj = Project.builder()
			.setProperties(Collections.singletonMap("froma", "9"))
			.build(main);
		assertThat(prj.getMainSourceSet().getDependencies(), containsInAnyOrder(
				"org.example:main:1",
				"org.example:a:1",
				"org.example:a-only:9",
				"org.example:b:1",
				"org.example:b-froma:9",
				"org.example:b-fromb:4",
				"org.example:c-froma:9",
				"org.example:c-fromb:none"));
	}

	private Path setupDiamond(Path dir, String valueA, String valueC) throws IOException {
		write(dir.resolve("Main.java"), "//PROPS common=1\n"
				+ "//SOURCES A.java C.java\n"
				+ "public class Main { public static void main(String... args) {} }\n");
		write(dir.resolve("A.java"), "//PROPS v=" + valueA + "\n"
				+ "//SOURCES D.java\n"
				+ "class A {}\n");
		write(dir.resolve("C.java"), "//PROPS v=" + valueC + "\n"
				+ "//SOURCES D.java\n"
				+ "class C {}\n");
		write(dir.resolve("D.java"), "//DEPS org.example:d:${v}\n"
				+ "//DEPS org.example:d-common:${common}\n"
				+ "class D {}\n");
		return dir.resolve("Main.java");
	}

	@Test
	void testDiamondWithDifferentPropertiesWarns(@TempDir Path dir) throws Exception {
		Path main = setupDiamond(dir, "1", "2");
		CaptureResult<Project> result = captureOutput(() -> Project.builder().build(main));
		assertThat(result.err, containsString("[WARN] Source '"));
		assertThat(result.err, containsString(
				"D.java' is included multiple times with different property values, only the values from its first inclusion will be used"));
		// D is only processed once, using the properties it inherited from A
		assertThat(result.result.getMainSourceSet().getDependencies(), containsInAnyOrder(
				"org.example:d:1",
				"org.example:d-common:1"));
	}

	@Test
	void testDiamondWithSamePropertiesDoesNotWarn(@TempDir Path dir) throws Exception {
		Path main = setupDiamond(dir, "1", "1");
		CaptureResult<Project> result = captureOutput(() -> Project.builder().build(main));
		assertThat(result.err, not(containsString("[WARN]")));
		assertThat(result.result.getMainSourceSet().getDependencies(), containsInAnyOrder(
				"org.example:d:1",
				"org.example:d-common:1"));
	}
}
