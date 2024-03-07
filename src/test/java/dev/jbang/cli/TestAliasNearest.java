package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.util.Util;

public class TestAliasNearest extends BaseTest {

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
	void initEach() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), global.getBytes());
		Path cwd = Files.createDirectory(cwdDir.resolve("test"));
		Util.setCwd(cwd);
		Path parentDotDir = Files.createDirectory(cwdDir.resolve(".jbang"));
		Files.write(parentDotDir.resolve(Catalog.JBANG_CATALOG_JSON), parent.getBytes());
		Path testDotDir = Files.createDirectory(cwdDir.resolve("test/.jbang"));
		Files.write(testDotDir.resolve(Catalog.JBANG_CATALOG_JSON), dotlocal.getBytes());
		Files.write(cwd.resolve(Catalog.JBANG_CATALOG_JSON), local.getBytes());
		Files.write(cwd.resolve("dummy.java"), "// Dummy Java File".getBytes());
	}

	@Test
	void testList() throws IOException {
		Catalog catalog = Catalog.getMerged(true, false);
		assertThat(catalog, notNullValue());

		HashSet<String> keys = new HashSet<>(Arrays.asList(
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
		assertThat(catalog.aliases.keySet(), equalTo(keys));

		assertThat(catalog.aliases.get("global1").scriptRef, is("global1ref"));
		assertThat(catalog.aliases.get("global2").scriptRef, is("global2inparent"));
		assertThat(catalog.aliases.get("global3").scriptRef, is("global3indotlocal"));
		assertThat(catalog.aliases.get("global4").scriptRef, is("global4inlocal"));

		assertThat(catalog.aliases.get("parent1").scriptRef, is("parent1ref"));
		assertThat(catalog.aliases.get("parent2").scriptRef, is("parent2indotlocal"));
		assertThat(catalog.aliases.get("parent3").scriptRef, is("parent3inlocal"));

		assertThat(catalog.aliases.get("dotlocal1").scriptRef, is("dotlocal1ref"));
		assertThat(catalog.aliases.get("dotlocal2").scriptRef, is("dotlocal2inlocal"));

		assertThat(catalog.aliases.get("local1").scriptRef, is("local1ref"));
	}

	@Test
	void testAddLocalUrl() throws IOException {
		testAddLocal("http://dummy", "http://dummy");
	}

	@Test
	void testAddLocalFile() throws IOException {
		testAddLocal("dummy.java", "dummy.java");
	}

	void testAddLocal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addNearestAlias("new", new Alias().withScriptRef(ref));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo(result));
	}

	@Test
	void testAddLocalExplicit() throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addAlias(Paths.get(Catalog.JBANG_CATALOG_JSON), "new", new Alias().withScriptRef("dummy.java"));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo("dummy.java"));
	}

	@Test
	void testAddDotLocalUrl() throws IOException {
		testAddDotLocal("http://dummy", "http://dummy");
	}

	@Test
	void testAddDotLocalFile() throws IOException {
		testAddDotLocal("dummy.java", Paths.get("../dummy.java").toString());
	}

	void testAddDotLocal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		CatalogUtil.addNearestAlias("new", new Alias().withScriptRef(ref));
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(dotLocalCatalog);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo(result));
	}

	@Test
	void testAddParentUrl() throws IOException {
		testAddParent("http://dummy", "http://dummy");
	}

	@Test
	void testAddParentFile() throws IOException {
		testAddParent("dummy.java", Paths.get("../test/dummy.java").toString());
	}

	void testAddParent(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		CatalogUtil.addNearestAlias("new", new Alias().withScriptRef(ref));
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(parentCatalog);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo(result));
	}

	@Test
	void testAddGlobalUrl() throws IOException {
		testAddGlobal("http://dummy", "http://dummy");
	}

	@Test
	void testAddGlobalFile() throws IOException {
		Path cwd = Util.getCwd();
		testAddGlobal("dummy.java",
				Paths.get("..", cwd.getParent().getFileName().toString(), "test/dummy.java").toString());
	}

	void testAddGlobal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		Files.delete(parentCatalog);
		CatalogUtil.addNearestAlias("new", new Alias().withScriptRef(ref));
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		assertThat(parentCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(Settings.getUserCatalogFile());
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo(result));
	}

	@Test
	void testRemoveLocal() throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestAlias("local1");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.aliases.keySet(), not(hasItem("local1")));
	}

	@Test
	void testRemoveDotLocal() throws IOException {
		Path cwd = Util.getCwd();
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestAlias("dotlocal1");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(dotLocalCatalog);
		assertThat(catalog.aliases.keySet(), not(hasItem("dotlocal1")));
	}

	@Test
	void testRemoveParent() throws IOException {
		Path cwd = Util.getCwd();
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestAlias("parent1");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(parentCatalog);
		assertThat(catalog.aliases.keySet(), not(hasItem("parent1")));
	}

	@Test
	void testRemoveGlobal() throws IOException {
		Path globalCatalog = Settings.getUserCatalogFile();
		CatalogUtil.removeNearestAlias("global1");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(globalCatalog);
		assertThat(catalog.aliases.keySet(), not(hasItem("global1")));
	}

}
