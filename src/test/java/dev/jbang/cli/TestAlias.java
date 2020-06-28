package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsAliasInfo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.jbang.Settings;
import picocli.CommandLine;

public class TestAlias {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"scriptRef\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"scriptRef\": \"one\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"properties\": {\"two\":\"2\"}\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"scriptRef\": \"http://dummy\",\n" +
			"      \"arguments\": [\"3\"],\n" +
			"      \"properties\": {\"three\":\"3\"}\n" +
			"    },\n" +
			"    \"four\": {\n" +
			"      \"scriptRef\": \"three\",\n" +
			"      \"arguments\": [\"4\"],\n" +
			"      \"properties\": {\"four\":\"4\"}\n" +
			"    },\n" +
			"    \"five\": {\n" +
			"      \"scriptRef\": \"three\"\n" +
			"    },\n" +
			"    \"six\": {\n" +
			"      \"scriptRef\": \"seven\"\n" +
			"    },\n" +
			"    \"seven\": {\n" +
			"      \"scriptRef\": \"six\"\n" +
			"    },\n" +
			"    \"eight\": {\n" +
			"      \"scriptRef\": \"seven\"\n" +
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

	@Test
	void testGetAliasOne() throws IOException {
		Settings.Alias alias = Settings.getAlias("one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasOneWithArgs() throws IOException {
		Settings.Alias alias = Settings.getAlias("one", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasTwo() throws IOException {
		Settings.Alias alias = Settings.getAlias("two", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("2"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("two", "2"));
	}

	@Test
	void testGetAliasTwoWithArgs() throws IOException {
		Settings.Alias alias = Settings.getAlias("two", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasFour() throws IOException {
		Settings.Alias alias = Settings.getAlias("four", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("4"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("four", "4"));
	}

	@Test
	void testGetAliasFourWithArgs() throws IOException {
		Settings.Alias alias = Settings.getAlias("four", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasFive() throws IOException {
		Settings.Alias alias = Settings.getAlias("five", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("3"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("three", "3"));
	}

	@Test
	void testGetAliasFiveWithArgs() throws IOException {
		Settings.Alias alias = Settings.getAlias("five", Collections.singletonList("X"),
				Collections.singletonMap("foo", "bar"));
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("X"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testGetAliasLoop() throws IOException {
		try {
			Settings.Alias alias = Settings.getAlias("eight", null, null);
			Assert.fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(), containsString("seven"));
		}

	}

}
