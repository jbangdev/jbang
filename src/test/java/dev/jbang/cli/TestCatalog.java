package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;

import picocli.CommandLine;

public class TestCatalog extends BaseTest {

	static final String testCatalog = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"script-ref\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"script-ref\": \"one\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"properties\": {\"two\":\"2\"}\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static Path catsFile = null;
	static Path testCatalogFile = null;

	@BeforeEach
	void init() throws IOException {
		catsFile = jbangTempDir.resolve("jbang-catalog.json");
		testCatalogFile = cwdDir.resolve("test-catalog.json");
		Files.write(testCatalogFile, testCatalog.getBytes());
		clearSettingsCaches();
		CatalogUtil.addCatalogRef(catsFile, "test", testCatalogFile.toAbsolutePath().toString(), "Test catalog");
	}

	@Test
	void testAddSucceeded() throws IOException {
		assertThat(Files.isRegularFile(catsFile), is(true));
		String cat = new String(Files.readAllBytes(catsFile));
		assertThat(cat, containsString("\"test\""));
		assertThat(cat, containsString("test-catalog.json\""));

		assertThat(Catalog.get(catsFile).catalogs, hasKey("test"));
		assertThat(Catalog.get(catsFile).catalogs.get("test").catalogRef,
				is(testCatalogFile.toAbsolutePath().toString()));
	}

	@Test
	void testAddInvalidName() throws IOException {
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("catalog", "add", "invalid!", "dummy");
	}

	@Test
	void testGetAlias() throws IOException {
		Alias alias = Alias.get("one@test", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
	}

	@Test
	void testUpdate() throws IOException {
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("catalog", "update");
	}

	@Test
	void testRemove() throws IOException {
		assertThat(Catalog.get(catsFile).catalogs, hasKey("test"));
		CatalogUtil.removeCatalogRef(catsFile, "test");
		assertThat(Catalog.get(catsFile).catalogs, not(hasKey("test")));
	}
}
