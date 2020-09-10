package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;

public class TestAliasNearest {

	static final String global = "{\n" +
			"  \"aliases\": {\n" +
			"    \"global1\": {\n" +
			"      \"script-ref\": \"global1ref\"\n" +
			"    },\n" +
			"    \"global2\": {\n" +
			"      \"script-ref\": \"global2ref\"\n" +
			"    },\n" +
			"    \"global3\": {\n" +
			"      \"script-ref\": \"global3ref\"\n" +
			"    },\n" +
			"    \"global4\": {\n" +
			"      \"script-ref\": \"global4ref\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static final String parent = "{\n" +
			"  \"aliases\": {\n" +
			"    \"parent1\": {\n" +
			"      \"script-ref\": \"parent1ref\"\n" +
			"    },\n" +
			"    \"parent2\": {\n" +
			"      \"script-ref\": \"parent2ref\"\n" +
			"    },\n" +
			"    \"parent3\": {\n" +
			"      \"script-ref\": \"parent3ref\"\n" +
			"    },\n" +
			"    \"global2\": {\n" +
			"      \"script-ref\": \"global2inparent\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static final String dotlocal = "{\n" +
			"  \"aliases\": {\n" +
			"    \"dotlocal1\": {\n" +
			"      \"script-ref\": \"dotlocal1ref\"\n" +
			"    },\n" +
			"    \"dotlocal2\": {\n" +
			"      \"script-ref\": \"dotlocal2ref\"\n" +
			"    },\n" +
			"    \"parent2\": {\n" +
			"      \"script-ref\": \"parent2indotlocal\"\n" +
			"    },\n" +
			"    \"global3\": {\n" +
			"      \"script-ref\": \"global3indotlocal\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static final String local = "{\n" +
			"  \"aliases\": {\n" +
			"    \"local1\": {\n" +
			"      \"script-ref\": \"local1ref\"\n" +
			"    },\n" +
			"    \"dotlocal2\": {\n" +
			"      \"script-ref\": \"dotlocal2inlocal\"\n" +
			"    },\n" +
			"    \"parent3\": {\n" +
			"      \"script-ref\": \"parent3inlocal\"\n" +
			"    },\n" +
			"    \"global4\": {\n" +
			"      \"script-ref\": \"global4inlocal\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void init() throws IOException {
		jbangTempDir.create();
		testTempDir.create();
		environmentVariables.set("JBANG_DIR", jbangTempDir.getRoot().getPath());
		Files.write(jbangTempDir.getRoot().toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), global.getBytes());
		File parentDotDir = testTempDir.newFolder(".jbang");
		Files.write(parentDotDir.toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), parent.getBytes());
		cwd = testTempDir.newFolder("test").toPath();
		File testDotDir = testTempDir.newFolder("test", ".jbang");
		Files.write(testDotDir.toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), dotlocal.getBytes());
		Files.write(cwd.resolve(AliasUtil.JBANG_CATALOG_JSON), local.getBytes());
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path cwd;

	@Test
	void testList() throws IOException {
		AliasUtil.Aliases aliases = AliasUtil.getAllAliasesFromLocalCatalogs(cwd);
		assertThat(aliases, notNullValue());

		HashSet<String> keys = new HashSet<String>(Arrays.asList(
				"global1",
				"global2",
				"global3",
				"global4",
				"parent1",
				"parent2",
				"parent3",
				"dotlocal1",
				"dotlocal2",
				"local1"));
		assertThat(aliases.aliases.keySet(), equalTo(keys));

		assertThat(aliases.aliases.get("global1").scriptRef, is("global1ref"));
		assertThat(aliases.aliases.get("global2").scriptRef, is("global2inparent"));
		assertThat(aliases.aliases.get("global3").scriptRef, is("global3indotlocal"));
		assertThat(aliases.aliases.get("global4").scriptRef, is("global4inlocal"));

		assertThat(aliases.aliases.get("parent1").scriptRef, is("parent1ref"));
		assertThat(aliases.aliases.get("parent2").scriptRef, is("parent2indotlocal"));
		assertThat(aliases.aliases.get("parent3").scriptRef, is("parent3inlocal"));

		assertThat(aliases.aliases.get("dotlocal1").scriptRef, is("dotlocal1ref"));
		assertThat(aliases.aliases.get("dotlocal2").scriptRef, is("dotlocal2inlocal"));

		assertThat(aliases.aliases.get("local1").scriptRef, is("local1ref"));
	}

	@Test
	void testAddLocal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		AliasUtil.addNearestAlias(cwd, "new", "http://dummy", null, null, null);
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(localCatalog, true);
		assertThat(aliases.aliases.keySet(), hasItem("new"));
	}

	@Test
	void testAddDotLocal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "http://dummy", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(dotLocalCatalog, true);
		assertThat(aliases.aliases.keySet(), hasItem("new"));
	}

	@Test
	void testAddParent() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "http://dummy", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(parentCatalog, true);
		assertThat(aliases.aliases.keySet(), hasItem("new"));
	}

	@Test
	void testAddGlobal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		Files.delete(parentCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "http://dummy", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		assertThat(parentCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(Settings.getAliasesFile(), true);
		assertThat(aliases.aliases.keySet(), hasItem("new"));
	}

	@Test
	void testRemoveLocal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		AliasUtil.removeNearestAlias(cwd, "local1");
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(localCatalog, true);
		assertThat(aliases.aliases.keySet(), not(hasItem("local1")));
	}

	@Test
	void testRemoveDotLocal() throws IOException {
		Path dotLocalCatalog = cwd.resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		AliasUtil.removeNearestAlias(cwd, "dotlocal1");
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(dotLocalCatalog, true);
		assertThat(aliases.aliases.keySet(), not(hasItem("dotlocal1")));
	}

	@Test
	void testRemoveParent() throws IOException {
		Path parentCatalog = cwd.getParent().resolve(AliasUtil.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		AliasUtil.removeNearestAlias(cwd, "parent1");
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(parentCatalog, true);
		assertThat(aliases.aliases.keySet(), not(hasItem("parent1")));
	}

	@Test
	void testRemoveGlobal() throws IOException {
		Path globalCatalog = Settings.getAliasesFile();
		AliasUtil.removeNearestAlias(cwd, "global1");
		clearSettingsCaches();
		AliasUtil.Aliases aliases = AliasUtil.getAliasesFromCatalogFile(globalCatalog, true);
		assertThat(aliases.aliases.keySet(), not(hasItem("global1")));
	}

}
