package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;

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
	void init(@TempDir Path tmpPath) throws IOException {
		Files.write(jbangTempDir.getRoot().toPath().resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		cwd = Files.createDirectory(tmpPath.resolve("test"));
	}

	private Path cwd;

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		assertThat(Alias.get(cwd, "one"), notNullValue());
	}

	@Test
	void testAddWithDefaultCatalogFile(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testAddWithDescriptionInScript(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
		assertThat(name.description, is("Description of the script inside the script"));
	}

	@Test
	void testAddWithDescriptionInArgs(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name",
				"-d", "Description of the script in arguments", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
		// The argument has more precedance than the description tag in the script
		assertThat(name.description, is("Description of the script in arguments"));
	}

	@Test
	void testAddWithMultipleDescriptionTags(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description first tag\n" +
				"//DESCRIPTION description second tag\n" +
				"//DESCRIPTION description third tag").getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
		assertThat(name.description,
				is("Description first tag\ndescription second tag\ndescription third tag"));
	}

	@Test
	void testAddWithNoDescriptionTags(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, ("// Test file \n").getBytes());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get(tmpPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
		assertThat(name.description,
				nullValue());
	}

	@Test
	void testAddWithHiddenJbangCatalog(@TempDir Path tmpPath) throws IOException {
		Path testFile = tmpPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJbangPath = Paths.get(tmpPath.toString(), CatalogUtil.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJbangPath);
		Files.createFile(Paths.get(tmpPath.toString(), CatalogUtil.JBANG_DOT_DIR, Catalog.JBANG_CATALOG_JSON));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", tmpPath.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(tmpPath.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Alias name = Alias.get(hiddenJbangPath, "name");
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path jbangPath = jbangTempDir.getRoot().toPath();
		Path testFile = jbangPath.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "-f", jbangPath.toString(), "--name=name", testFile.toString());
		Alias one = Alias.get(jbangPath, "one");
		Alias name = Alias.get(jbangPath, "name");
		assertThat(one.scriptRef, is("http://dummy"));
		assertThat(name.scriptRef, is(testFile.toString()));
	}

	@Test
	void testGetAliasNone() throws IOException {
		Alias alias = Alias.get(cwd, "dummy-alias!", null, null);
		assertThat(alias, nullValue());
	}

	@Test
	void testGetAliasOne() throws IOException {
		Alias alias = Alias.get(cwd, "one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(cwd), equalTo("http://dummy"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasOneWithArgs() throws IOException {
		Alias alias = Alias.get(cwd, "one", Collections.singletonList("X"),
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
		Alias alias = Alias.get(cwd, "two", null, null);
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
		Alias alias = Alias.get(cwd, "two", Collections.emptyList(), Collections.emptyMap());
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
		Alias alias = Alias.get(cwd, "two", Collections.singletonList("X"),
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
		Alias alias = Alias.get(cwd, "four", null, null);
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
		Alias alias = Alias.get(cwd, "four", Collections.singletonList("X"),
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
		Alias alias = Alias.get(cwd, "five", null, null);
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
		Alias alias = Alias.get(cwd, "five", Collections.singletonList("X"),
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
		Alias alias = Alias.get(cwd, "gav", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(cwd), equalTo("org.example:artifact:version"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasLoop() throws IOException {
		try {
			Alias.get(cwd, "eight", null, null);
			Assert.fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(), containsString("seven"));
		}

	}

}
