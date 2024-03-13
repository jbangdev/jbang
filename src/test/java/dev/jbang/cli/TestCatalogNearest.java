package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;

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
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogRef;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.util.Util;

public class TestCatalogNearest extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"test\": {\n" +
			"      \"script-ref\": \"testref\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void initEach() throws IOException {
		aliasesFile = cwdDir.resolve("aliases.json");
		Files.write(aliasesFile, aliases.getBytes());
		parentDotDir = Files.createDirectory(cwdDir.resolve(".jbang"));
		Path cwd = Files.createDirectory(cwdDir.resolve("test"));
		Util.setCwd(cwd);
		testDotDir = Files.createDirectory(cwd.resolve(".jbang"));
		CatalogUtil.addCatalogRef(cwd.resolve(Catalog.JBANG_CATALOG_JSON), "local", aliasesFile.toString(),
				"Local", null);
		CatalogUtil.addCatalogRef(testDotDir.resolve(Catalog.JBANG_CATALOG_JSON), "dotlocal",
				aliasesFile.toString(), "Local .jbang", null);
		CatalogUtil.addCatalogRef(parentDotDir.resolve(Catalog.JBANG_CATALOG_JSON), "dotparent",
				aliasesFile.toString(), "Parent .jbang", null);
		CatalogUtil.addCatalogRef(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), "global",
				aliasesFile.toString(), "Global", null);
	}

	private Path aliasesFile;
	private Path testDotDir;
	private Path parentDotDir;

	@Test
	void testFilesCreated() {
		Path cwd = Util.getCwd();
		assertThat(cwd.resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(testDotDir.resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(parentDotDir.resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
	}

	@Test
	void testList() throws IOException {
		Catalog catalog = Catalog.getMerged(true, false);
		assertThat(catalog, notNullValue());

		HashSet<String> keys = new HashSet<>(Arrays.asList(
				"global",
				"dotparent",
				"dotlocal",
				"local",
				"jbanghub"));
		assertThat(catalog.catalogs.keySet().containsAll(keys), is(true));

		assertThat(catalog.catalogs.get("global").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("dotparent").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("dotlocal").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("local").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("jbanghub").catalog, is(Catalog.getBuiltin()));

		catalog.catalogs.keySet().removeAll(keys);
		// After removing the known keys, the rest must come from the jbanghub import
		final String JBANGHUB_URL = "https://raw.githubusercontent.com/jbanghub/jbang-catalog/main/jbang-catalog.json";
		for (CatalogRef c : catalog.catalogs.values()) {
			assertThat(c.catalog.catalogRef.getOriginalResource(), is(JBANGHUB_URL));
		}
	}

	@Test
	void testAddLocalFile() throws IOException {
		testAddLocal(aliasesFile.toString(), aliasesFile.toString());
	}

	@Test
	void testAddLocalUrl() throws IOException {
		testAddLocal("https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json",
				"https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json");
	}

	@Test
	void testAddLocalImplicit() throws IOException {
		testAddLocal("jbangdev", "https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json");
	}

	void testAddLocal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addNearestCatalogRef("new", ref, null, false);
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(result));
	}

	@Test
	void testAddLocalExplicit() throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addCatalogRef(Paths.get(Catalog.JBANG_CATALOG_JSON), "new", aliasesFile.toString(), null, null);
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(aliasesFile.toString()));
	}

	@Test
	void testAddDotLocalFile() throws IOException {
		testAddDotLocal(aliasesFile.toString(), aliasesFile.toString());
	}

	void testAddDotLocal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		CatalogUtil.addNearestCatalogRef("new", ref, null, false);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(dotLocalCatalog);
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(result));
	}

	@Test
	void testAddParentFile() throws IOException {
		testAddParent(aliasesFile.toString(), aliasesFile.toString());
	}

	void testAddParent(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		CatalogUtil.addNearestCatalogRef("new", ref, null, false);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(parentCatalog);
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(result));
	}

	@Test
	void testAddGlobalFile() throws IOException {
		testAddGlobal(aliasesFile.toString(), aliasesFile.toString());
	}

	void testAddGlobal(String ref, String result) throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		Files.delete(parentCatalog);
		CatalogUtil.addNearestCatalogRef("new", ref, null, false);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		assertThat(parentCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		Catalog catalog = Catalog.get(Settings.getUserCatalogFile());
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(result));
	}

	@Test
	void testRemoveLocal() throws IOException {
		Path cwd = Util.getCwd();
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef("local");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("local")));
	}

	@Test
	void testRemoveDotLocal() throws IOException {
		Path cwd = Util.getCwd();
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef("dotlocal");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(dotLocalCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("dotlocal")));
	}

	@Test
	void testRemoveParent() throws IOException {
		Path cwd = Util.getCwd();
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef("dotparent");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(parentCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("dotparent")));
	}

	@Test
	void testRemoveGlobal() throws IOException {
		Path globalCatalog = Settings.getUserCatalogFile();
		CatalogUtil.removeNearestCatalogRef("global");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(globalCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("global")));
	}
}
