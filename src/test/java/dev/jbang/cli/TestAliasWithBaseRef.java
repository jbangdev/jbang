package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

public class TestAliasWithBaseRef extends BaseTest {

	static final String aliases = "{\n" +
			"  \"base-ref\": \"http://dummy\",\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"script-ref\": \"foo\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"script-ref\": \"foo/bar.java\"\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"script-ref\": \"http://dummy/baz.java\"\n" +
			"    },\n" +
			"    \"gav\": {\n" +
			"      \"script-ref\": \"org.example:artifact:version\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void initEach() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testGetAliasOne() throws IOException {
		Alias alias = Alias.get("one");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo"));
		assertThat(alias.resolve(), equalTo("http://dummy/foo"));
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Alias alias = Alias.get("two");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo/bar.java"));
		assertThat(alias.resolve(), equalTo("http://dummy/foo/bar.java"));
	}

	@Test
	void testGetAliasThree() throws IOException {
		Alias alias = Alias.get("three");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy/baz.java"));
		assertThat(alias.resolve(), equalTo("http://dummy/baz.java"));
	}

	@Test
	void testGetAliasGav() throws IOException {
		Alias alias = Alias.get("gav");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(), equalTo("org.example:artifact:version"));
	}

}
