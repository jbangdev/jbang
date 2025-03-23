package dev.jbang.cli;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("foo");
		assertThat(alias.resolve()).isEqualTo("http://dummy/foo");
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Alias alias = Alias.get("two");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("foo/bar.java");
		assertThat(alias.resolve()).isEqualTo("http://dummy/foo/bar.java");
	}

	@Test
	void testGetAliasThree() throws IOException {
		Alias alias = Alias.get("three");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("http://dummy/baz.java");
		assertThat(alias.resolve()).isEqualTo("http://dummy/baz.java");
	}

	@Test
	void testGetAliasGav() throws IOException {
		Alias alias = Alias.get("gav");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("org.example:artifact:version");
		assertThat(alias.resolve()).isEqualTo("org.example:artifact:version");
	}

}
