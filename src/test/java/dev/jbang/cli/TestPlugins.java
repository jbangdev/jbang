package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.matchesRegex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Catalog;

public class TestPlugins extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"jbang-one\": {\n" +
			"      \"script-ref\": \"./one.java\",\n" +
			"      \"description\": \"plugin one\"\n" +
			"    },\n" +
			"    \"jbang-two\": {\n" +
			"      \"script-ref\": \"./two.java\",\n" +
			"      \"description\": \"plugin two\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void initCatalog() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		Path one = jbangTempDir.resolve("one.java");
		Files.writeString(one,
				"public class one { public static void main(String[] args) { System.out.println(\"output plugin one\"); } }");
		Path two = jbangTempDir.resolve("two.java");
		Files.writeString(two,
				"public class two { public static void main(String[] args) { System.out.println(\"output plugin two\"); } }");
	}

	@Test
	void testListPlugins() throws Exception {
		CaptureResult<Integer> result = checkedRun(null, "--help");
		Pattern p = Pattern.compile(".*External:\\R\\s+one\\s+plugin\\sone\\R\\s+two\\s+plugin\\stwo\\R.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		assertThat(result.err, matchesPattern(p));
	}

	@Test
	void testRunPlugin() throws Exception {
		CaptureResult<Integer> result = checkedRun(null, "one");
		assertThat(result.out.trim(), matchesRegex(".+java.+one\\.jar.+one"));
	}
}
