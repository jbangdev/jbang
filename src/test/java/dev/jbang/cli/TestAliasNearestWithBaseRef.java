package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;
import dev.jbang.BaseTest;
import dev.jbang.Settings;

public class TestAliasNearestWithBaseRef extends BaseTest {

	static final String global = "{\n" +
			"  \"aliases\": {\n" +
			"  }\n" +
			"}";

	static final String parent = "{\n" +
			"  \"base-ref\": \"../scripts\",\n" +
			"  \"aliases\": {\n" +
			"  }\n" +
			"}";

	static final String dotlocal = "{\n" +
			"  \"base-ref\": \"../scripts\",\n" +
			"  \"aliases\": {\n" +
			"  }\n" +
			"}";

	static final String local = "{\n" +
			"  \"base-ref\": \"scripts\",\n" +
			"  \"aliases\": {\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void init() throws IOException {
		testTempDir.create();
		Files.write(jbangTempDir.getRoot().toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), global.getBytes());
		File parentDotDir = testTempDir.newFolder(".jbang");
		Files.write(parentDotDir.toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), parent.getBytes());
		File parentScriptsDir = testTempDir.newFolder("scripts");
		Files.write(parentScriptsDir.toPath().resolve("parent.java"), "// Dummy Java File".getBytes());
		cwd = testTempDir.newFolder("test").toPath();
		File testDotDir = testTempDir.newFolder("test", ".jbang");
		Files.write(testDotDir.toPath().resolve(AliasUtil.JBANG_CATALOG_JSON), dotlocal.getBytes());
		Files.write(cwd.resolve(AliasUtil.JBANG_CATALOG_JSON), local.getBytes());
		Files.write(cwd.resolve("dummy.java"), "// Dummy Java File".getBytes());
		File localScriptsDir = testTempDir.newFolder("test", "scripts");
		Files.write(localScriptsDir.toPath().resolve("local.java"), "// Dummy Java File".getBytes());
	}

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path cwd;

	@Test
	void testAddLocal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		AliasUtil.addNearestAlias(cwd, "new", "scripts/local.java", null, null, null);
		clearSettingsCaches();
		AliasUtil.Catalog catalog = AliasUtil.getCatalog(localCatalog, true);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo("local.java"));
	}

	@Test
	void testAddDotLocal() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "scripts/local.java", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Catalog catalog = AliasUtil.getCatalog(dotLocalCatalog, true);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo("local.java"));
	}

	@Test
	void testAddParent1() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "scripts/local.java", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Catalog catalog = AliasUtil.getCatalog(parentCatalog, true);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef.replace('\\', '/'), equalTo("../test/scripts/local.java"));
	}

	@Test
	void testAddParent2() throws IOException {
		Path localCatalog = cwd.resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path dotLocalCatalog = cwd.resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Path parentCatalog = cwd.getParent().resolve(Settings.JBANG_DOT_DIR).resolve(AliasUtil.JBANG_CATALOG_JSON);
		Files.delete(localCatalog);
		Files.delete(dotLocalCatalog);
		AliasUtil.addNearestAlias(cwd, "new", "../scripts/parent.java", null, null, null);
		assertThat(localCatalog.toFile(), not(anExistingFile()));
		assertThat(dotLocalCatalog.toFile(), not(anExistingFile()));
		clearSettingsCaches();
		AliasUtil.Catalog catalog = AliasUtil.getCatalog(parentCatalog, true);
		assertThat(catalog.aliases.keySet(), hasItem("new"));
		assertThat(catalog.aliases.get("new").scriptRef, equalTo("parent.java"));
	}

}
