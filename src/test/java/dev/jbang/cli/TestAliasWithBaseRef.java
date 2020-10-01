package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;

public class TestAliasWithBaseRef {

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
	void init() throws IOException {
		jbangTempDir.create();
		testTempDir.create();
		environmentVariables.set("JBANG_DIR", jbangTempDir.getRoot().getPath());
		Files.write(jbangTempDir.getRoot().toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), aliases.getBytes());
		cwd = testTempDir.newFolder("test").toPath();
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path cwd;

	@Test
	void testGetAliasOne() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/foo"));
	}

	@Test
	void testGetAliasTwo() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "two", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("foo/bar.java"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/foo/bar.java"));
	}

	@Test
	void testGetAliasThree() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "three", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy/baz.java"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy/baz.java"));
	}

	@Test
	void testGetAliasGav() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "gav", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(cwd), equalTo("org.example:artifact:version"));
	}

}
