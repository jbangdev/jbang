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
			"    },\n" +
			"    \"fj\": {\n" +
			"      \"script-ref\": \"i.look:like-a-gav:1.0.0.Beta5@fatjar\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static Path catsFile = null;
	static Path testCatalogFile = null;

	@BeforeEach
	void initEach() throws IOException {
		catsFile = jbangTempDir.resolve("jbang-catalog.json");
		testCatalogFile = cwdDir.resolve("test-catalog.json");
		Files.write(testCatalogFile, testCatalog.getBytes());
		clearSettingsCaches();
		CatalogUtil.addCatalogRef(catsFile, "test", testCatalogFile.toAbsolutePath().toString(), "Test catalog", null);
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
		JBang.getCommandLine().execute("catalog", "add", "--name=invalid!", "dummy");
	}

	@Test
	void testGetAlias() throws IOException {
		Alias alias = Alias.get("one@test");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
	}

	@Test
	void testGetFatJar() throws IOException {
		Alias alias = Alias.get("fj@test");
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("i.look:like-a-gav:1.0.0.Beta5@fatjar"));
	}

	@Test
	void testUpdate() throws IOException {
		JBang.getCommandLine().execute("catalog", "update");
	}

	@Test
	void testRemove() throws IOException {
		assertThat(Catalog.get(catsFile).catalogs, hasKey("test"));
		CatalogUtil.removeCatalogRef(catsFile, "test");
		assertThat(Catalog.get(catsFile).catalogs, not(hasKey("test")));
	}

	@Test
	void testNameFromGAV() throws IOException {
		String name = CatalogUtil.nameFromRef("com.intuit.karate:karate-core:LATEST");
		assertThat(name, equalTo("karate-core"));
	}

	@Test
	void testNameFromReleasedJar() throws IOException {
		String name = CatalogUtil
			.nameFromRef("https://github.com/blazmrak/veles/releases/download/0.1.0/veles-0.1.0.jar");
		assertThat(name, equalTo("veles"));
	}

	@Test
	void testNameFromHyphenatedName() throws IOException {
		String name = CatalogUtil.nameFromRef("my-app.jar");
		assertThat(name, equalTo("my-app"));
	}

	@Test
	void testNameFromHyphenatedVersionedName() throws IOException {
		String name = CatalogUtil.nameFromRef("my-app-1.2.3.jar");
		assertThat(name, equalTo("my-app"));
	}

	@Test
	void testNameFromMixedVersions() throws IOException {
		String name = CatalogUtil.nameFromRef("my-app-1.2.a.jar");
		assertThat(name, equalTo("my-app"));
	}
}
