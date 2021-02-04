package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.catalog.AliasUtil;
import dev.jbang.util.Util;

public class TestUtil extends BaseTest {
	public static void clearSettingsCaches() {
		AliasUtil.clearCache();
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

	@Test
	void testExplode() throws IOException {
		Path baseDir = examplesTestFolder.toPath();

		String source = ".";

		final List<String> p = Util.explode(source, baseDir, "**/*.java");

		assertThat(p, hasItem("res/resource.java"));
		assertThat(p, not(hasItem("hello.jsh")));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "**/*.jsh"));

		assertThat(p, not(hasItem("res/resource.java")));
		assertThat(p, not(hasItem("test.java")));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "res/resource.java"));

		assertThat(p, containsInAnyOrder("res/resource.java"));
		assertThat(p, not(hasItem("test.java")));

	}

	@Test
	void testDispostionFilename() throws IOException {
		assertThat(Util.getDispositionFilename("inline; filename*=iso-8859-1'en'%A3%20rates"), equalTo("£ rates"));
		assertThat(Util.getDispositionFilename("inline; filename*=\"UTF-8''%c2%a3%20and%20%e2%82%ac%20rates\""),
				equalTo("£ and € rates"));
	}
}
