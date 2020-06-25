package dk.xam.jbang.cli;

import static dk.xam.jbang.TestUtil.clearSettingsAliasInfo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

public class TestAlias {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"scriptRef\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"scriptRef\": \"http://dummy\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	static Path jbangTempDir = null;

	@BeforeAll
	static void init() throws IOException {
		jbangTempDir = Files.createTempDirectory("jbang");
		environmentVariables.set("JBANG_DIR", jbangTempDir.toString());
		Files.write(jbangTempDir.resolve("aliases.json"), aliases.getBytes());
	}

	@AfterAll
	static void cleanup() throws IOException {
		if (jbangTempDir != null) {
			Files	.walk(jbangTempDir)
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}
	}

	@Rule
	public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsAliasInfo();
		assertThat(Settings.getAliases().get("one"), notNullValue());
	}

	@Test
	void testAdd() throws IOException {
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "new", "http://dummy");
		clearSettingsAliasInfo();
		assertThat(Settings.getAliases().get("new").scriptRef, equalTo("http://dummy"));
	}

	@Test
	void testRemove() throws IOException {
		clearSettingsAliasInfo();
		System.err.println(Settings.getAliases().toString());
		assertThat(Settings.getAliases().get("two"), notNullValue());
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "remove", "two");
		clearSettingsAliasInfo();
		assertThat(Settings.getAliases().get("two"), nullValue());
	}

}
