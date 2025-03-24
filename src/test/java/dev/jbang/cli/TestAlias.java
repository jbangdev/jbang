package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
			"      \"runtime-options\": [\"--two\"],\n" +
			"      \"sources\": [\"twosrc\"],\n" +
			"      \"files\": [\"twofiles\"],\n" +
			"      \"dependencies\": [\"twodep\"],\n" +
			"      \"repositories\": [\"tworepo\"],\n" +
			"      \"classpaths\": [\"twocp\"],\n" +
			"      \"properties\": {\"two\":\"2\"},\n" +
			"      \"java\": \"twojava\",\n" +
			"      \"main\": \"twomain\",\n" +
			"      \"module\": \"twomodule\",\n" +
			"      \"compile-options\": [\"--ctwo\"],\n" +
			"      \"native-image\": true,\n" +
			"      \"native-options\": [\"--ntwo\"],\n" +
			"      \"jfr\": \"twojfr\",\n" +
			"      \"debug\": {\"twod\":\"2\"},\n" +
			"      \"cds\": true,\n" +
			"      \"interactive\": true,\n" +
			"      \"enable-preview\": true,\n" +
			"      \"enable-assertions\": true,\n" +
			"      \"enable-system-assertions\": true,\n" +
			"      \"manifest-options\": {\"twom\":\"2\"},\n" +
			"      \"java-agents\": [{\"agent-ref\":\"twojag\",\"options\":\"twoopts\"}]\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"script-ref\": \"http://dummy\",\n" +
			"      \"arguments\": [\"3\"],\n" +
			"      \"runtime-options\": [\"--three\"],\n" +
			"      \"compile-options\": [\"--cthree\"],\n" +
			"      \"native-image\": true,\n" +
			"      \"native-options\": [\"--nthree\"],\n" +
			"      \"properties\": {\"three\":\"3\"}\n" +
			"    },\n" +
			"    \"four\": {\n" +
			"      \"script-ref\": \"three\",\n" +
			"      \"description\": \"fourdesc\",\n" +
			"      \"arguments\": [\"4\"],\n" +
			"      \"runtime-options\": [\"--four\"],\n" +
			"      \"sources\": [\"foursrc\"],\n" +
			"      \"files\": [\"fourfiles\"],\n" +
			"      \"dependencies\": [\"fourdep\"],\n" +
			"      \"repositories\": [\"fourrepo\"],\n" +
			"      \"classpaths\": [\"fourcp\"],\n" +
			"      \"properties\": {\"four\":\"4\"},\n" +
			"      \"java\": \"fourjava\",\n" +
			"      \"main\": \"fourmain\",\n" +
			"      \"module\": \"fourmodule\",\n" +
			"      \"compile-options\": [\"--cfour\"],\n" +
			"      \"native-image\": false,\n" +
			"      \"native-options\": [\"--nfour\"],\n" +
			"      \"jfr\": \"fourjfr\",\n" +
			"      \"debug\": {\"fourd\":\"4\"},\n" +
			"      \"cds\": false,\n" +
			"      \"interactive\": false,\n" +
			"      \"enable-preview\": false,\n" +
			"      \"enable-assertions\": false,\n" +
			"      \"enable-system-assertions\": false,\n" +
			"      \"manifest-options\": {\"fourm\":\"4\"},\n" +
			"      \"java-agents\": [{\"agent-ref\":\"fourjag\",\"options\":\"fouropts\"}]\n" +
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
	void initCatalog() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		assertThat(Alias.get("one")).isNotNull();
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef).isEqualTo("test.java");
	}

	@Test
	void testAddWithDefaultCatalogFile2() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("alias", "add",
						"-f", cwd.toString(),
						"--name=name",
						"--description", "desc",
						"--deps", "deps",
						"--repos", "repos",
						"--cp", "cps",
						"--runtime-option", "jopts",
						"-D", "prop=val",
						"--main", "mainclass",
						"--compile-option", "copts",
						"--native",
						"--native-option", "nopts",
						"--java", "999",
						testFile.toString(),
						"aap", "noot", "mies");
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef).isEqualTo("test.java");
		assertThat(alias.description).isEqualTo("desc");
		assertThat(alias.runtimeOptions, iterableWithSize(1));
		assertThat(alias.runtimeOptions).containsExactly("jopts");
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies).containsExactly("deps");
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories).containsExactly("repos");
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths).containsExactly("cps");
		assertThat(alias.properties).hasSize(1);
		assertThat(alias.properties).containsEntry("prop", "val");
		assertThat(alias.mainClass).isEqualTo("mainclass");
		assertThat(alias.compileOptions, iterableWithSize(1));
		assertThat(alias.compileOptions).containsExactly("copts");
		assertThat(alias.nativeImage).isEqualTo(Boolean.TRUE);
		assertThat(alias.nativeOptions, iterableWithSize(1));
		assertThat(alias.nativeOptions).containsExactly("nopts");
		assertThat(alias.javaVersion).isEqualTo("999");
		assertThat(alias.arguments).containsExactly("aap", "noot", "mies");
	}

	@Test
	void testAddWithDefaultCatalogFile3() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path catFile = Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON);
		Files.write(catFile, "".getBytes());
		assertThat(Files.size(catFile)).isEqualTo(0L);
		JBang	.getCommandLine()
				.execute("alias", "add",
						"--name=name",
						"--description", "desc",
						"--deps", "deps",
						"--repos", "repos",
						"--cp", "cps",
						"--runtime-option", "jopts",
						"-D", "prop=val",
						"--main", "mainclass",
						"--compile-option", "copts",
						"--native-option", "nopts",
						"--java", "999",
						testFile.toString(),
						"aap", "noot", "mies");
		assertThat(Files.size(catFile), not(is(0L)));
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef).isEqualTo("test.java");
		assertThat(alias.description).isEqualTo("desc");
		assertThat(alias.runtimeOptions, iterableWithSize(1));
		assertThat(alias.runtimeOptions).containsExactly("jopts");
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies).containsExactly("deps");
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories).containsExactly("repos");
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths).containsExactly("cps");
		assertThat(alias.properties).hasSize(1);
		assertThat(alias.properties).containsEntry("prop", "val");
		assertThat(alias.arguments, iterableWithSize(3));
		assertThat(alias.mainClass).isEqualTo("mainclass");
		assertThat(alias.compileOptions, iterableWithSize(1));
		assertThat(alias.compileOptions).containsExactly("copts");
		assertThat(alias.nativeImage).isNull();
		assertThat(alias.nativeOptions, iterableWithSize(1));
		assertThat(alias.nativeOptions).containsExactly("nopts");
		assertThat(alias.javaVersion).isEqualTo("999");
		assertThat(alias.arguments).containsExactly("aap", "noot", "mies");
	}

	@Test
	void testAddWithSubDir() throws IOException {
		Path cwd = Util.getCwd();
		Path sub = Files.createDirectory(cwd.resolve("sub"));
		Path testFile = sub.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias name = Alias.get("name");
		assertThat(name.scriptRef).isEqualTo(Paths.get("sub/test.java").toString());
	}

	@Test
	void testAddWithDescriptionInScript() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias name = Alias.get("name");
		assertThat(name.scriptRef).isEqualTo("test.java");
		assertThat(name.description).isEqualTo("Description of the script inside the script");
	}

	@Test
	void testAddWithDescriptionInArgs() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description of the script inside the script").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("alias", "add", "-f", cwd.toString(), "--name=name",
						"--description", "Description of the script in arguments", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias name = Alias.get("name");
		assertThat(name.scriptRef).isEqualTo("test.java");
		// The argument has more precedance than the description tag in the script
		assertThat(name.description).isEqualTo("Description of the script in arguments");
	}

	@Test
	void testAddWithMultipleDescriptionTags() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n" +
				"//DESCRIPTION Description first tag\n" +
				"//DESCRIPTION description second tag\n" +
				"//DESCRIPTION description third tag").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias name = Alias.get("name");
		assertThat(name.scriptRef).isEqualTo("test.java");
		assertThat(name.description).isEqualTo("Description first tag\ndescription second tag\ndescription third tag");
	}

	@Test
	void testAddWithNoDescriptionTags() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, ("// Test file \n").getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias name = Alias.get("name");
		assertThat(name.scriptRef).isEqualTo("test.java");
		assertThat(name.description).isNull();
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
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		Catalog catalog = Catalog.get(hiddenJBangPath);
		Alias name = catalog.aliases.get("name");
		assertThat(name.scriptRef).isEqualTo(Paths.get("../sub/test.java").toString());
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		JBang.getCommandLine().execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		Alias one = Alias.get("one");
		Alias name = Alias.get("name");
		assertThat(one.scriptRef).isEqualTo("http://dummy");
		assertThat(name.scriptRef).isEqualTo("test.java");
	}

	@Test
	void testAddWithRepos() throws IOException {
		String jar = "dummygroup:dummyart:0.1";
		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("alias", "add", "--name=aliaswithrepo", "--repos",
													"https://dummyrepo", jar);
		AliasAdd add = (AliasAdd) pr.subcommand().subcommand().commandSpec().userObject();
		try {
			add.doCall();
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString()).contains("Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to https://dummyrepo");
		}
	}

	@Test
	void testAddMissingScript() {
		assertThrows(IllegalArgumentException.class, () -> {
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("alias", "add", "--name=name");
			AliasAdd add = (AliasAdd) pr.subcommand().subcommand().commandSpec().userObject();
			add.doCall();
		});
	}

	@Test
	void testAddExisting() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path testFile2 = cwd.resolve("test2.java");
		Files.write(testFile2, "// Test file 2".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		int exitCode = JBang.getCommandLine()
							.execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_OK);
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Alias alias = Alias.get("name");
		assertThat(alias.scriptRef).isEqualTo("test.java");
		exitCode = JBang.getCommandLine()
						.execute("alias", "add", "-f", cwd.toString(), "--name=name", testFile2.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_INVALID_INPUT);
		exitCode = JBang.getCommandLine()
						.execute("alias", "add", "-f", cwd.toString(), "--name=name", "--force", testFile2.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_OK);
		alias = Alias.get("name");
		assertThat(alias.scriptRef).isEqualTo("test2.java");
	}

	@Test
	void testGetAliasNone() throws IOException {
		Alias alias = Alias.get("dummy-alias!");
		assertThat(alias).isNull();
	}

	@Test
	void testGetAliasOne() throws IOException {
		Alias alias = Alias.get("one");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("http://dummy");
		assertThat(alias.resolve()).isEqualTo("http://dummy");
		assertThat(alias.arguments).isNull();
		assertThat(alias.runtimeOptions).isNull();
		assertThat(alias.properties).isNull();
		assertThat(alias.compileOptions).isNull();
		assertThat(alias.nativeImage).isNull();
		assertThat(alias.nativeOptions).isNull();
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Alias alias = Alias.get("two");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("http://dummy");
		assertThat(alias.resolve()).isEqualTo("http://dummy");
		assertThat(alias.description).isEqualTo("twodesc");
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments).containsExactly("2");
		assertThat(alias.runtimeOptions, iterableWithSize(1));
		assertThat(alias.runtimeOptions).containsExactly("--two");
		assertThat(alias.sources, iterableWithSize(1));
		assertThat(alias.sources).containsExactly("twosrc");
		assertThat(alias.resources, iterableWithSize(1));
		assertThat(alias.resources).containsExactly("twofiles");
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies).containsExactly("twodep");
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories).containsExactly("tworepo");
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths).containsExactly("twocp");
		assertThat(alias.properties).hasSize(1);
		assertThat(alias.properties).containsEntry("two", "2");
		assertThat(alias.javaVersion).isEqualTo("twojava");
		assertThat(alias.mainClass).isEqualTo("twomain");
		assertThat(alias.moduleName).isEqualTo("twomodule");
		assertThat(alias.compileOptions, iterableWithSize(1));
		assertThat(alias.compileOptions).containsExactly("--ctwo");
		assertThat(alias.nativeImage).isEqualTo(Boolean.TRUE);
		assertThat(alias.nativeOptions, iterableWithSize(1));
		assertThat(alias.nativeOptions).containsExactly("--ntwo");
		assertThat(alias.jfr).isEqualTo("twojfr");
		assertThat(alias.debug).hasSize(1);
		assertThat(alias.debug).containsEntry("twod", "2");
		assertThat(alias.cds).isEqualTo(Boolean.TRUE);
		assertThat(alias.interactive).isEqualTo(Boolean.TRUE);
		assertThat(alias.enablePreview).isEqualTo(Boolean.TRUE);
		assertThat(alias.enableAssertions).isEqualTo(Boolean.TRUE);
		assertThat(alias.enableSystemAssertions).isEqualTo(Boolean.TRUE);
		assertThat(alias.manifestOptions).hasSize(1);
		assertThat(alias.manifestOptions).containsEntry("twom", "2");
		assertThat(alias.javaAgents, iterableWithSize(1));
		assertThat(alias.javaAgents).containsExactly(new Alias.JavaAgent("twojag", "twoopts"));
	}

	@Test
	void testGetAliasFour() throws IOException {
		Alias alias = Alias.get("four");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("http://dummy");
		assertThat(alias.resolve()).isEqualTo("http://dummy");
		assertThat(alias.description).isEqualTo("fourdesc");
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments).containsExactly("4");
		assertThat(alias.runtimeOptions, iterableWithSize(1));
		assertThat(alias.runtimeOptions).containsExactly("--four");
		assertThat(alias.sources, iterableWithSize(1));
		assertThat(alias.sources).containsExactly("foursrc");
		assertThat(alias.resources, iterableWithSize(1));
		assertThat(alias.resources).containsExactly("fourfiles");
		assertThat(alias.dependencies, iterableWithSize(1));
		assertThat(alias.dependencies).containsExactly("fourdep");
		assertThat(alias.repositories, iterableWithSize(1));
		assertThat(alias.repositories).containsExactly("fourrepo");
		assertThat(alias.classpaths, iterableWithSize(1));
		assertThat(alias.classpaths).containsExactly("fourcp");
		assertThat(alias.properties).hasSize(1);
		assertThat(alias.properties).containsEntry("four", "4");
		assertThat(alias.javaVersion).isEqualTo("fourjava");
		assertThat(alias.mainClass).isEqualTo("fourmain");
		assertThat(alias.moduleName).isEqualTo("fourmodule");
		assertThat(alias.compileOptions, iterableWithSize(1));
		assertThat(alias.compileOptions).containsExactly("--cfour");
		assertThat(alias.nativeImage).isEqualTo(Boolean.FALSE);
		assertThat(alias.nativeOptions, iterableWithSize(1));
		assertThat(alias.nativeOptions).containsExactly("--nfour");
		assertThat(alias.jfr).isEqualTo("fourjfr");
		assertThat(alias.debug).hasSize(1);
		assertThat(alias.debug).containsEntry("fourd", "4");
		assertThat(alias.cds).isEqualTo(Boolean.FALSE);
		assertThat(alias.interactive).isEqualTo(Boolean.FALSE);
		assertThat(alias.enablePreview).isEqualTo(Boolean.FALSE);
		assertThat(alias.enableAssertions).isEqualTo(Boolean.FALSE);
		assertThat(alias.enableSystemAssertions).isEqualTo(Boolean.FALSE);
		assertThat(alias.manifestOptions).hasSize(1);
		assertThat(alias.manifestOptions).containsEntry("fourm", "4");
		assertThat(alias.javaAgents, iterableWithSize(1));
		assertThat(alias.javaAgents).containsExactly(new Alias.JavaAgent("fourjag", "fouropts"));
	}

	@Test
	void testGetAliasFive() throws IOException {
		Alias alias = Alias.get("five");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("http://dummy");
		assertThat(alias.resolve()).isEqualTo("http://dummy");
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments).containsExactly("3");
		assertThat(alias.runtimeOptions, iterableWithSize(1));
		assertThat(alias.runtimeOptions).containsExactly("--three");
		assertThat(alias.properties).hasSize(1);
		assertThat(alias.properties).containsEntry("three", "3");
		assertThat(alias.compileOptions, iterableWithSize(1));
		assertThat(alias.compileOptions).containsExactly("--cthree");
		assertThat(alias.nativeImage).isEqualTo(Boolean.TRUE);
		assertThat(alias.nativeOptions, iterableWithSize(1));
		assertThat(alias.nativeOptions).containsExactly("--nthree");
	}

	@Test
	void testGetAliasGav() throws IOException {
		Alias alias = Alias.get("gav");
		assertThat(alias).isNotNull();
		assertThat(alias.scriptRef).isEqualTo("org.example:artifact:version");
		assertThat(alias.resolve()).isEqualTo("org.example:artifact:version");
		assertThat(alias.arguments).isNull();
		assertThat(alias.runtimeOptions).isNull();
		assertThat(alias.properties).isNull();
		assertThat(alias.compileOptions).isNull();
		assertThat(alias.nativeImage).isNull();
		assertThat(alias.nativeOptions).isNull();
	}

	@Test
	void testGetAliasLoop() throws IOException {
		try {
			Alias.get("eight");
			fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage()).contains("seven");
		}

	}

}
