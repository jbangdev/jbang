package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestAlias extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"script-ref\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"script-ref\": \"one\",\n" +
			"      \"description\": \"twodesc\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"java-options\": [\"--two\"],\n" +
			"      \"dependencies\": [\"twodep\"],\n" +
			"      \"repositories\": [\"tworepo\"],\n" +
			"      \"classpaths\": [\"twocp\"],\n" +
			"      \"properties\": {\"two\":\"2\"}\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"script-ref\": \"http://dummy\",\n" +
			"      \"arguments\": [\"3\"],\n" +
			"      \"java-options\": [\"--three\"],\n" +
			"      \"properties\": {\"three\":\"3\"}\n" +
			"    },\n" +
			"    \"four\": {\n" +
			"      \"script-ref\": \"three\",\n" +
			"      \"description\": \"fourdesc\",\n" +
			"      \"arguments\": [\"4\"],\n" +
			"      \"java-options\": [\"--four\"],\n" +
			"      \"dependencies\": [\"fourdep\"],\n" +
			"      \"repositories\": [\"fourrepo\"],\n" +
			"      \"classpaths\": [\"fourcp\"],\n" +
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
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		assertThat(Alias.get("one"), notNullValue());
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef, is("test.java"));
	}

	@Test
	void testAddWithDefaultCatalogFile2() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add",
				"-f", cwd.toString(),
				"--name=name", testFile.toString(),
				"--description", "desc",
				"--deps", "deps",
				"--repos", "repos",
				"--cp", "cps",
				"--java-options", "jopts",
				"-D", "prop=val",
				"--main", "mainclass",
				"--java", "version",
				"aap", "noot", "mies");
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef, is("test.java"));
		assertThat(alias.description, is("desc"));
		assertThat(alias.javaOptions, iterableWithSize(1));
		assertThat(alias.javaOptions, contains("jopts"));
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies, contains("deps"));
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories, contains("repos"));
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths, contains("cps"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("prop", "val"));
		assertThat(alias.arguments, iterableWithSize(3));
		assertThat(alias.mainClass, is("mainclass"));
		assertThat(alias.javaVersion, is("version"));
		assertThat(alias.arguments, contains("aap", "noot", "mies"));
	}

	@Test
	void testAddWithDefaultCatalogFile3() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path catFile = Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON);
		Files.write(catFile, "".getBytes());
		assertThat(Files.size(catFile), is(0L));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add",
				"--name=name", testFile.toString(),
				"--description", "desc",
				"--deps", "deps",
				"--repos", "repos",
				"--cp", "cps",
				"--java-options", "jopts",
				"-D", "prop=val",
				"--main", "mainclass",
				"--java", "version",
				"aap", "noot", "mies");
		assertThat(Files.size(catFile), not(is(0L)));
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef, is("test.java"));
		assertThat(alias.description, is("desc"));
		assertThat(alias.javaOptions, iterableWithSize(1));
		assertThat(alias.javaOptions, contains("jopts"));
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies, contains("deps"));
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories, contains("repos"));
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths, contains("cps"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("prop", "val"));
		assertThat(alias.arguments, iterableWithSize(3));
		assertThat(alias.mainClass, is("mainclass"));
		assertThat(alias.javaVersion, is("version"));
		assertThat(alias.arguments, contains("aap", "noot", "mies"));
	}

	@Test
	void testAddWithSubDir() throws IOException {
		Path cwd = Util.getCwd();
		Path sub = Files.createDirectory(cwd.resolve("sub"));
		Path testFile = sub.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get("name");
		assertThat(name.scriptRef, is(Paths.get("sub/test.java").toString()));
	}

	@Test
	void testAddWithDescriptionInScript() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get("name");
		assertThat(name.scriptRef, is("test.java"));
		assertThat(name.description, is("Description of the script inside the script"));
	}

	@Test
	void testAddWithDescriptionInArgs() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name",
				"-d", "Description of the script in arguments", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get("name");
		assertThat(name.scriptRef, is("test.java"));
		// The argument has more precedance than the description tag in the script
		assertThat(name.description, is("Description of the script in arguments"));
	}

	@Test
	void testAddWithMultipleDescriptionTags() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description first tag\n" +
				"//DESCRIPTION description second tag\n" +
				"//DESCRIPTION description third tag").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get("name");
		assertThat(name.scriptRef, is("test.java"));
		assertThat(name.description,
				is("Description first tag\ndescription second tag\ndescription third tag"));
	}

	@Test
	void testAddWithNoDescriptionTags() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Alias name = Alias.get("name");
		assertThat(name.scriptRef, is("test.java"));
		assertThat(name.description,
				nullValue());
	}

	@Test
	void testAddWithHiddenJBangCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path sub = Files.createDirectory(cwd.resolve("sub"));
		Path testFile = sub.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJBangPath = Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJBangPath);
		Files.createFile(Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR, Catalog.JBANG_CATALOG_JSON));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Catalog catalog = Catalog.get(hiddenJBangPath);
		Alias name = catalog.aliases.get("name");
		assertThat(name.scriptRef, is(Paths.get("../sub/test.java").toString()));
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		Alias one = Alias.get("one");
		Alias name = Alias.get("name");
		assertThat(one.scriptRef, is("http://dummy"));
		assertThat(name.scriptRef, is("test.java"));
	}

	@Test
	void testGetAliasNone() throws IOException {
		Alias alias = Alias.get("dummy-alias!");
		assertThat(alias, nullValue());
	}

	@Test
	void testGetAliasOne() throws IOException {
		Alias alias = Alias.get("one");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(), equalTo("http://dummy"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.javaOptions, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Alias alias = Alias.get("two");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(), equalTo("http://dummy"));
		assertThat(alias.description, equalTo("twodesc"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("2"));
		assertThat(alias.javaOptions, iterableWithSize(1));
		assertThat(alias.javaOptions, contains("--two"));
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies, contains("twodep"));
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories, contains("tworepo"));
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths, contains("twocp"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("two", "2"));
	}

	@Test
	void testGetAliasFour() throws IOException {
		Alias alias = Alias.get("four");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(), equalTo("http://dummy"));
		assertThat(alias.description, equalTo("fourdesc"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("4"));
		assertThat(alias.javaOptions, iterableWithSize(1));
		assertThat(alias.javaOptions, contains("--four"));
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies, contains("fourdep"));
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories, contains("fourrepo"));
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths, contains("fourcp"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("four", "4"));
	}

	@Test
	void testGetAliasFive() throws IOException {
		Alias alias = Alias.get("five");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.resolve(), equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("3"));
		assertThat(alias.javaOptions, iterableWithSize(1));
		assertThat(alias.javaOptions, contains("--three"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("three", "3"));
	}

	@Test
	void testGetAliasGav() throws IOException {
		Alias alias = Alias.get("gav");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("org.example:artifact:version"));
		assertThat(alias.resolve(), equalTo("org.example:artifact:version"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.javaOptions, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasLoop() throws IOException {
		try {
			Alias.get("eight");
			Assert.fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(), containsString("seven"));
		}

	}

}
