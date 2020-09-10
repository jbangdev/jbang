package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;

import picocli.CommandLine;

public class TestCatalog {

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

	static Path catInfoFile = null;
	static Path testCatalogFile = null;

	@BeforeEach
	void init() throws IOException {
		jbangTempDir.create();
		catalogTempDir.create();
		catInfoFile = jbangTempDir.getRoot().toPath().resolve("catalogs.json");
		environmentVariables.set("JBANG_DIR", jbangTempDir.getRoot().getPath());
		testCatalogFile = catalogTempDir.getRoot().toPath().resolve("test-catalog.json");
		Files.write(testCatalogFile, testCatalog.getBytes());

		clearSettingsCaches();
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("catalog", "add", "test", testCatalogFile.toAbsolutePath().toString());
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();

	@Rule
	public final TemporaryFolder catalogTempDir = new TemporaryFolder();

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	void testAddSucceeded() throws IOException {
		assertThat(Files.isRegularFile(catInfoFile), is(true));
		String cat = new String(Files.readAllBytes(catInfoFile));
		assertThat(cat, containsString("\"test\""));
		assertThat(cat, containsString("test-catalog.json\""));

		assertThat(Settings.getCatalogs(), hasKey("test"));
		assertThat(Settings.getCatalogs().get("test").catalogRef, is(testCatalogFile.toAbsolutePath().toString()));
	}

	@Test
	void testAddInvalidName() throws IOException {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage(containsString("Invalid catalog name"));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("catalog", "add", "invalid!", "dummy");
	}

	@Test
	void testGetAlias() throws IOException {
		AliasUtil.Alias alias = AliasUtil.getAlias(null, "one@test", null, null);
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
		assertThat(Settings.getCatalogs(), hasKey("test"));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("catalog", "remove", "test");
		assertThat(Settings.getCatalogs(), not(hasKey("test")));
	}
}
