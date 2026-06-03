package dev.jbang.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;

public class TestDocs extends BaseTest {

	static final String WIDGET_SRC = "package demo;\n\n"
			+ "public class Widget {\n"
			+ "  /** The name. */\n"
			+ "  public String name;\n"
			+ "  public Widget(String name) {}\n"
			+ "  public String greet(String who) { return \"hi\"; }\n"
			+ "}\n";

	@Test
	void testDocsLocalSourceMarkdown(@TempDir Path tmp) throws Exception {
		Path script = tmp.resolve("Widget.java");
		Files.writeString(script, WIDGET_SRC);

		CaptureResult<Integer> result = checkedRun(null, "docs", script.toString());

		assertThat(result.result).isEqualTo(BaseCommand.EXIT_OK);
		// Output is written via Util.infoMsg which goes to stderr
		assertThat(result.err).contains("Widget");
		assertThat(result.err).contains("Fields");
		assertThat(result.err).contains("Methods");
	}

	@Test
	void testDocsLocalSourceJson(@TempDir Path tmp) throws Exception {
		Path script = tmp.resolve("Widget.java");
		Files.writeString(script, WIDGET_SRC);

		CaptureResult<Integer> result = checkedRun(null, "docs", "--json", script.toString());

		assertThat(result.result).isEqualTo(BaseCommand.EXIT_OK);
		assertThat(result.err).contains("\"schema\"");
		assertThat(result.err).contains("\"types\"");
	}

	@Test
	void testDocsDirectory(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("Alpha.java"), "package demo;\npublic class Alpha {}\n");
		Files.writeString(tmp.resolve("Beta.java"), "package demo;\npublic class Beta { public int x; }\n");

		CaptureResult<Integer> result = checkedRun(null, "docs", tmp.toString());

		assertThat(result.result).isEqualTo(BaseCommand.EXIT_OK);
		assertThat(result.err).contains("Alpha");
		assertThat(result.err).contains("Beta");
	}

	@Test
	void testDocsTypeFilter(@TempDir Path tmp) throws Exception {
		String src = "package demo;\n"
				+ "public class Foo { public int x; }\n";
		String src2 = "package demo;\n"
				+ "public class Bar { public int y; }\n";
		Files.writeString(tmp.resolve("Foo.java"), src);
		Files.writeString(tmp.resolve("Bar.java"), src2);

		CaptureResult<Integer> result = checkedRun(null, "docs", "--type", "Foo", tmp.toString());

		assertThat(result.result).isEqualTo(BaseCommand.EXIT_OK);
		assertThat(result.err).contains("Foo");
		assertThat(result.err).doesNotContain("Bar");
	}

	@Test
	void testDocsUnsupportedType(@TempDir Path tmp) throws Exception {
		Path groovy = tmp.resolve("Hello.groovy");
		Files.writeString(groovy, "println 'hello'\n");

		ExitException ex = assertThrows(ExitException.class,
				() -> checkedRun(null, "docs", groovy.toString()));
		assertThat(ex.getMessage()).contains("groovy");
	}

	@Test
	void testDocsTargetNotFound(@TempDir Path tmp) throws Exception {
		Path missing = tmp.resolve("NoSuchFile.java");

		ExitException ex = assertThrows(ExitException.class,
				() -> checkedRun(null, "docs", missing.toString()));
		assertThat(ex.getMessage()).containsIgnoringCase("not found");
	}

	@Test
	void testDocsInvalidCoordinate() throws Exception {
		// "just-one-word" is not a file and not a coordinate (no colon)
		ExitException ex = assertThrows(ExitException.class,
				() -> checkedRun(null, "docs", "just-one-word"));
		assertThat(ex.getMessage()).isNotEmpty();
	}

	@Test
	void testDocsJarWithJavadocSibling(@TempDir Path tmp) throws Exception {
		// Create a minimal main JAR
		Path mainJar = tmp.resolve("mylib.jar");
		try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
				Files.newOutputStream(mainJar))) {
			jos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
			jos.write("Manifest-Version: 1.0\n".getBytes());
			jos.closeEntry();
		}

		// Create a minimal javadoc JAR with one HTML page in javadoc style
		Path javadocJar = tmp.resolve("mylib-javadoc.jar");
		String javadocHtml = "<!DOCTYPE HTML>\n"
				+ "<html><head><title>com.example.Foo</title></head>\n"
				+ "<body>\n"
				+ "<div class=\"type-signature\"><span class=\"modifiers\">public class </span>"
				+ "<span class=\"element-name\">Foo</span></div>\n"
				+ "<div class=\"block\">A simple Foo class.</div>\n"
				+ "</body></html>\n";
		try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
				Files.newOutputStream(javadocJar))) {
			jos.putNextEntry(new java.util.zip.ZipEntry("com/example/Foo.html"));
			jos.write(javadocHtml.getBytes());
			jos.closeEntry();
		}

		CaptureResult<Integer> result = checkedRun(null, "docs", mainJar.toString());

		assertThat(result.result).isEqualTo(BaseCommand.EXIT_OK);
	}
}
