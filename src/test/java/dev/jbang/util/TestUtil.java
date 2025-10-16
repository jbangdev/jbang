package dev.jbang.util;

import static dev.jbang.util.NetUtil.ConnectionConfigurator.authentication;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Catalog;

public class TestUtil extends BaseTest {
	public static void clearSettingsCaches() {
		Catalog.clearCache();
	}

	@Test
	void testGetSourcePackage() {
		assertEquals("blah", Util.getSourcePackage("package blah;").get());
		assertEquals("blahpackage", Util.getSourcePackage("package blahpackage;").get());
		assertEquals("blahpackagewonka", Util.getSourcePackage("package blahpackagewonka;").get());

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

		// the Java 21 / JEP-445

		content = "class HelloWorld {\n" +
				"\tvoid main() {\n" +
				"\t\tSystem.out.println(\"Hello world from an instance main method o/\");\n" +
				"\t}\n" +
				"}";
		assertTrue(Util.hasMainMethod(content));

		content = "void main() {\n" +
				"\t\tSystem.out.println(\"Hello world from an unnamed main method o/\");\n" +
				"\t}\n";
		assertTrue(Util.hasMainMethod(content));

	}

	@Test
	void testExplode() throws IOException {
		Path baseDir = examplesTestFolder;

		String source = ".";

		final List<String> p = Util.explode(source, baseDir, "**.java");

		assertThat(p, hasItem("res/resource.java"));
		assertThat(p, not(hasItem("hello.jsh")));
		assertThat(p, hasItem("quote.java"));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "**/*.java"));

		assertThat(p, hasItem("res/resource.java"));
		assertThat(p, not(hasItem("quote.java")));
		assertThat(p, not(hasItem("main.jsh")));

		p.clear();
		p.addAll(Util.explode(source, baseDir, "res/resource.java"));

		assertThat(p, containsInAnyOrder("res/resource.java"));
		assertThat(p, not(hasItem("test.java")));

	}

	@Test
	void testExplodeAbs() throws IOException {
		Path baseDir = examplesTestFolder;

		String source = ".";
		String dir = examplesTestFolder.toString().replace('\\', '/');

		final List<String> p = Util.explode(source, cwdDir, dir + "/**.java");

		assertThat(p, hasItem(dir + "/res/resource.java"));
		assertThat(p, not(hasItem(dir + "/hello.jsh")));
		assertThat(p, hasItem(dir + "/quote.java"));

		p.clear();
		p.addAll(Util.explode(source, cwdDir, dir + "/**/*.java"));

		assertThat(p, hasItem(dir + "/res/resource.java"));
		assertThat(p, not(hasItem(dir + "/quote.java")));
		assertThat(p, not(hasItem(dir + "/main.jsh")));

		p.clear();
		p.addAll(Util.explode(source, cwdDir, dir + "/res/resource.java"));

		assertThat(p, containsInAnyOrder(dir + "/res/resource.java"));
		assertThat(p, not(hasItem(dir + "/test.java")));

	}

	@Test
	void testDispostionFilename() {
		assertThat(NetUtil.getDispositionFilename("inline; filename=token"), equalTo("token"));
		assertThat(NetUtil.getDispositionFilename("inline; filename=\"quoted string\""), equalTo("quoted string"));
		assertThat(NetUtil.getDispositionFilename("inline; filename*=iso-8859-1'en'%A3%20rates"), equalTo("£ rates"));
		assertThat(NetUtil.getDispositionFilename("inline; filename*=UTF-8''%c2%a3%20and%20%e2%82%ac%20rates"),
				equalTo("£ and € rates"));
		assertThat(NetUtil.getDispositionFilename("inline; filename=token; filename*=iso-8859-1'en'%A3%20rates"),
				equalTo("£ rates"));
		// The spec actually tells us to always use filename* but our implementation is
		// too dumb for that
		assertThat(NetUtil.getDispositionFilename("inline; filename*=iso-8859-1'en'%A3%20rates; filename=token"),
				equalTo("token"));
		// assertThat(Util.getDispositionFilename("inline;
		// filename*=iso-fake-1''dummy"), equalTo(""));
	}

	@Test
	void testUrlBasicAuth() throws IOException {
		System.setProperty("SOME_USERNAME", "JohnDoe");
		System.setProperty("SOME_PASSWORD", "VeryStrongPassword1");
		URLConnection connection = new NoOpUrlConnection(
				new URL("https://${SOME_USERNAME}:${SOME_PASSWORD}@example.com"));

		authentication().configure(connection);

		assertThat(connection.getRequestProperty("Authorization"),
				equalTo("Basic Sm9obkRvZTpWZXJ5U3Ryb25nUGFzc3dvcmQx"));
	}

	@Test
	void testToJavaIdentifier() {
		// dont get a leading underscore if a valid name
		assertThat(Util.toJavaIdentifier("HelloWorld"), equalTo("HelloWorld"));
		assertThat(Util.toJavaIdentifier("_underscore"), equalTo("_underscore"));
		assertThat(Util.toJavaIdentifier("Ångström"), equalTo("Ångström"));
		assertThat(Util.toJavaIdentifier("café"), equalTo("café"));

		// Leading digits are handled by the extra underscore
		assertThat(Util.toJavaIdentifier("123abc"), equalTo("_123abc"));
		assertThat(Util.toJavaIdentifier("0test"), equalTo("_0test"));

		// Invalid characters are replaced with underscores
		assertThat(Util.toJavaIdentifier("foo-bar"), equalTo("_foo_bar"));
		assertThat(Util.toJavaIdentifier("hello world"), equalTo("_hello_world"));
		assertThat(Util.toJavaIdentifier("my.package.Class"), equalTo("_my_package_Class"));
		assertThat(Util.toJavaIdentifier("test+plus"), equalTo("_test_plus"));
	}
}
