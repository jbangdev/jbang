package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;

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
	void init(@TempDir Path tmpPath) throws IOException {
		Files.write(jbangTempDir.getRoot().toPath().resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		cwd = Files.createDirectory(tmpPath.resolve("test"));
	}

	private Path cwd;

	@Test
	void testGetAliasOne() throws IOException {
		Alias alias = Alias.get(cwd, "one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/foo"));
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Alias alias = Alias.get(cwd, "two", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo/bar.java"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/foo/bar.java"));
	}

	@Test
	void testGetAliasThree() throws IOException {
		Alias alias = Alias.get(cwd, "three", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy/baz.java"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/baz.java"));
	}

	@Test
	void testGetAliasGav() throws IOException {
		Alias alias = Alias.get(cwd, "gav", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(cwd), equalTo("org.example:artifact:version"));
	}

}
