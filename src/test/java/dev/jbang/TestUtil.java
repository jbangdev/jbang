package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.jbang.cli.TestRun;

public class TestUtil {
	public static void clearSettingsCaches() {
		AliasUtil.catalogCache.clear();
	}

	@Test
	void testHasMainMethod() {

		// it's a false positive but it doesn't matter, because the code
		// would not compile anyway.
		assertTrue(Util.hasMainMethod("WOWpublic static void main("));

		// this is an edge case: when it's inside a String ...
		// But I don't think we need to handle it now
		assertTrue(Util.hasMainMethod("\"public static void main(\""));

		assertTrue(Util.hasMainMethod("public static void main("));
		assertTrue(Util.hasMainMethod("public static void main(String[] args)\n"));
		assertTrue(Util.hasMainMethod("public static void main(String... args)\n"));
		assertTrue(Util.hasMainMethod("    public static void main(String... args) {"));
		assertTrue(Util.hasMainMethod("    public static void main(String... args) {\n"));
		assertTrue(Util.hasMainMethod("class Main {public static void main("));
		assertTrue(Util.hasMainMethod("class Main { public static void main(\n"));
		assertTrue(Util.hasMainMethod("class Main {\tpublic static void main("));
		assertTrue(Util.hasMainMethod("class Main {\npublic static void main("));
		assertTrue(Util.hasMainMethod("class Main {\n\n    public static void main("));
		assertTrue(Util.hasMainMethod("       public   static   void   main  ("));
		assertTrue(Util.hasMainMethod("class Main {static public void main("));
		assertTrue(Util.hasMainMethod("       static   public   void   main  ( "));
		assertTrue(Util.hasMainMethod("}public static void main("));
		assertTrue(Util.hasMainMethod(";public static void main("));

		assertFalse(Util.hasMainMethod("class Main {public void main("));
		assertFalse(Util.hasMainMethod("class Main {static void main("));
		assertFalse(Util.hasMainMethod("public String helloWorld("));

		String content = "public class one { " +
				"              " +
				"public static void main(String... args) { " +
				"	out.println(\"ok\");" +
				"}" +
				"}";
		assertTrue(Util.hasMainMethod(content));

		content = "public static\nvoid\nmain(String\nargs[]) {";
		assertTrue(Util.hasMainMethod(content));
	}

	public static final String EXAMPLES_FOLDER = "examples";
	static File examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException, IOException {
		URL examplesUrl = TestRun.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		examplesTestFolder = new File(new File(examplesUrl.toURI()).getAbsolutePath());

		Settings.clearCache(Settings.CacheClass.jars);

	}

	@Test
	void testExplode() throws IOException {
		FileSystem fs = FileSystems.getDefault();
		Path baseDir = examplesTestFolder.toPath();

		String source = ".";

		final List<String> p = Util.explode(source, baseDir, "**/*.java");

		assertThat(p, hasItem("benchmark/perftest.java"));
		assertThat(p, not(hasItem("jetty.jsh")));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "**/*.jsh"));

		assertThat(p, not(hasItem("benchmark/perftest.java")));
		assertThat(p, not(hasItem("lang.java")));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "benchmark/perftest.java"));

		assertThat(p, containsInAnyOrder("benchmark/perftest.java"));
		assertThat(p, not(hasItem("lang.java")));

	}
}
