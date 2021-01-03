package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;
import dev.jbang.BaseTest;
import dev.jbang.Settings;

import picocli.CommandLine;

public class TestAlias extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"script-ref\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"script-ref\": \"one\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"properties\": {\"two\":\"2\"}\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"script-ref\": \"http://dummy\",\n" +
			"      \"arguments\": [\"3\"],\n" +
			"      \"properties\": {\"three\":\"3\"}\n" +
			"    },\n" +
			"    \"four\": {\n" +
			"      \"script-ref\": \"three\",\n" +
			"      \"arguments\": [\"4\"],\n" +
			"      \"properties\": {\"four\":\"4\"}\n" +
			"    },\n" +
			"    \"five\": {\n" +
			"      \"script-ref\": \"three\"\n" +
			"    },\n" +
			"    \"six\": {\n" +
			"      \"script-ref\": \"seven\"\n" +
			"    },\n" +
			"    \"seven\": {\n" +
			"      \"script-ref\": \"six\"\n" +
			"    },\n" +
			"    \"eight\": {\n" +
			"      \"script-ref\": \"seven\"\n" +
			"    },\n" +
			"    \"gav\": {\n" +
			"      \"script-ref\": \"org.example:artifact:version\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void init() throws IOException {
		testTempDir.create();
		Files.write(jbangTempDir.getRoot().toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), aliases.getBytes());
		cwd = testTempDir.newFolder("test").toPath();
	}

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path cwd;

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		assertThat(AliasUtil.getAlias(cwd, "one"), notNullValue());
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		TemporaryFolder tmp = new TemporaryFolder();
		tmp.create();
		Path tmpPath = tmp.getRoot().toPath();
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), AliasUtil.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmp.getRoot().toPath().toString(), AliasUtil.JBANG_CATALOG_JSON)),
				is(true));
		AliasUtil.Alias name = AliasUtil.getAlias(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testAddWithHiddenJbangCatalog() throws IOException {
		TemporaryFolder tmp = new TemporaryFolder();
		tmp.create();
		Path tmpPath = tmp.getRoot().toPath();
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJbangPath = Paths.get(tmpPath.toString(), Settings.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJbangPath);
		Files.createFile(Paths.get(tmpPath.toString(), Settings.JBANG_DOT_DIR, AliasUtil.JBANG_CATALOG_JSON));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), AliasUtil.JBANG_CATALOG_JSON)), is(false));
		AliasUtil.Alias name = AliasUtil.getAlias(hiddenJbangPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path jbangPath = jbangTempDir.getRoot().toPath();
		Path testFile = jbangPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", jbangPath.toString(), "--name=name", testFile.toString());
		AliasUtil.Alias one = AliasUtil.getAlias(jbangPath, "one");
		AliasUtil.Alias name = AliasUtil.getAlias(jbangPath, "name");
		assertThat(one.scriptRef, is("http://dummy"));
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testGetAliasNone() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "dummy-alias!", null, null);
		assertThat(alias, nullValue());
	}

	@Test
	void testGetAliasOne() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasOneWithArgs() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "one", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasTwo() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "two", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("2"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("two", "2"));
	}

	@Test
	void testGetAliasTwoAlt() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "two", Collections.emptyList(), Collections.emptyMap());
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("2"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("two", "2"));
	}

	@Test
	void testGetAliasTwoWithArgs() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "two", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasFour() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "four", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("4"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("four", "4"));
	}

	@Test
	void testGetAliasFourWithArgs() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "four", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasFive() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "five", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("3"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("three", "3"));
	}

	@Test
	void testGetAliasFiveWithArgs() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "five", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasGav() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(cwd, "gav", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(cwd), equalTo("org.example:artifact:version"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasLoop() throws IOException {
		try {
			AliasUtil.getAlias(cwd, "eight", null, null);
			Assert.fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(), containsString("seven"));
		}

	}

}
