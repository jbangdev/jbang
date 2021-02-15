package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;

public class TestCatalogNearest extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"test\": {\n" +
			"      \"script-ref\": \"testref\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void init() throws IOException {
		testTempDir.create();
		aliasesFile = testTempDir.getRoot().toPath().resolve("aliases.json");
		Files.write(aliasesFile, aliases.getBytes());
		parentDotDir = testTempDir.newFolder(".jbang");
		cwd = testTempDir.newFolder("test").toPath();
		testDotDir = testTempDir.newFolder("test", ".jbang");
		CatalogUtil.addCatalogRef(cwd, cwd.resolve(Catalog.JBANG_CATALOG_JSON), "local", aliasesFile.toString(),
				"Local");
		CatalogUtil.addCatalogRef(cwd, testDotDir.toPath().resolve(Catalog.JBANG_CATALOG_JSON), "dotlocal",
				aliasesFile.toString(), "Local .jbang");
		CatalogUtil.addCatalogRef(cwd, parentDotDir.toPath().resolve(Catalog.JBANG_CATALOG_JSON), "dotparent",
				aliasesFile.toString(), "Patent .jbang");
		CatalogUtil.addCatalogRef(cwd, jbangTempDir.getRoot().toPath().resolve(Catalog.JBANG_CATALOG_JSON), "global",
				aliasesFile.toString(), "Global");
	}

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path cwd;
	private Path aliasesFile;
	private File testDotDir;
	private File parentDotDir;

	@Test
	void testFilesCreated() {
		assertThat(cwd.resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(testDotDir.toPath().resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(parentDotDir.toPath().resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
		assertThat(jbangTempDir.getRoot().toPath().resolve(Catalog.JBANG_CATALOG_JSON).toFile(), anExistingFile());
	}

	@Test
	void testList() throws IOException {
		Catalog catalog = Catalog.getMerged(cwd, false);
		assertThat(catalog, notNullValue());

		HashSet<String> keys = new HashSet<>(Arrays.asList(
				"global",
				"dotparent",
				"dotlocal",
				"local"));
		assertThat(catalog.catalogs.keySet(), equalTo(keys));

		assertThat(catalog.catalogs.get("global").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("dotparent").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("dotlocal").catalogRef, is(aliasesFile.toString()));
		assertThat(catalog.catalogs.get("local").catalogRef, is(aliasesFile.toString()));
	}

	@Test
	void testAddLocalFile() throws IOException {
		testAddLocal(aliasesFile.toString(), aliasesFile.toString());
	}

	@Test
	void testAddLocalUrl() throws IOException {
		testAddLocal("https://github.com/jbangdev/jbang-catalog/blob/master/jbang-catalog.json",
				"https://github.com/jbangdev/jbang-catalog/blob/master/jbang-catalog.json");
	}

	@Test
	void testAddLocalImplicit() throws IOException {
		testAddLocal("jbangdev", "https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json");
	}

	void testAddLocal(String ref, String result) throws IOException {
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addNearestCatalogRef(cwd, "new", ref, null);
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.catalogs.keySet(), hasItem("new"));
		assertThat(catalog.catalogs.get("new").catalogRef, equalTo(result));
	}

	@Test
	void testAddLocalExplicit() throws IOException {
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.addCatalogRef(cwd, Paths.get(Catalog.JBANG_CATALOG_JSON), "new", aliasesFile.toString(), null);
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
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		CatalogUtil.addNearestCatalogRef(cwd, "new", ref, null);
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
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		CatalogUtil.addNearestCatalogRef(cwd, "new", ref, null);
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
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		Files.delete(parentCatalog);
		CatalogUtil.addNearestCatalogRef(cwd, "new", ref, null);
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
		Path localCatalog = cwd.resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef(cwd, "local");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(localCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("local")));
	}

	@Test
	void testRemoveDotLocal() throws IOException {
		Path dotLocalCatalog = cwd.resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef(cwd, "dotlocal");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(dotLocalCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("dotlocal")));
	}

	@Test
	void testRemoveParent() throws IOException {
		Path parentCatalog = cwd.getParent().resolve(CatalogUtil.JBANG_DOT_DIR).resolve(Catalog.JBANG_CATALOG_JSON);
		CatalogUtil.removeNearestCatalogRef(cwd, "dotparent");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(parentCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("dotparent")));
	}

	@Test
	void testRemoveGlobal() throws IOException {
		Path globalCatalog = Settings.getUserCatalogFile();
		CatalogUtil.removeNearestCatalogRef(cwd, "global");
		clearSettingsCaches();
		Catalog catalog = Catalog.get(globalCatalog);
		assertThat(catalog.catalogs.keySet(), not(hasItem("global")));
	}
}
