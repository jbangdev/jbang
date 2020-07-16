package dev.jbang.cli;

import static dev.jbang.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;

import picocli.CommandLine;

public class TestAlias {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"one\": {\n" +
			"      \"script-ref\": \"http://dummy\"\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"script-ref\": \"one\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"properties\": {\"two\":\"2\"}\n" +
			"    },\n" +
			"    \"three\": {\n" +
			"      \"script-ref\": \"http://dummy\",\n" +
			"      \"arguments\": [\"3\"],\n" +
			"      \"properties\": {\"three\":\"3\"}\n" +
			"    },\n" +
			"    \"four\": {\n" +
			"      \"script-ref\": \"three\",\n" +
			"      \"arguments\": [\"4\"],\n" +
			"      \"properties\": {\"four\":\"4\"}\n" +
			"    },\n" +
			"    \"five\": {\n" +
			"      \"script-ref\": \"three\"\n" +
			"    },\n" +
			"    \"six\": {\n" +
			"      \"script-ref\": \"seven\"\n" +
			"    },\n" +
			"    \"seven\": {\n" +
			"      \"script-ref\": \"six\"\n" +
			"    },\n" +
			"    \"eight\": {\n" +
			"      \"script-ref\": \"seven\"\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void init() throws IOException {
		jbangTempDir.create();
		environmentVariables.set("JBANG_DIR", jbangTempDir.getRoot().getPath());
		Files.write(jbangTempDir.getRoot().toPath().resolve("aliases.json"), aliases.getBytes());
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		assertThat(Settings.getAliases().get("one"), notNullValue());
	}

	@Test
	void testAdd() throws IOException {
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "add", "new", "http://dummy");
		clearSettingsCaches();
		assertThat(Settings.getAliases().get("new").scriptRef, equalTo("http://dummy"));
	}

	@Test
	void testRemove() throws IOException {
		clearSettingsCaches();
		System.err.println(Settings.getAliases().toString());
		assertThat(Settings.getAliases().get("two"), notNullValue());
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("alias", "remove", "two");
		clearSettingsCaches();
		assertThat(Settings.getAliases().get("two"), nullValue());
	}

	@Test
	void testGetAliasNone() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("dummy-alias!", null, null);
		assertThat(alias, nullValue());
	}

	@Test
	void testGetAliasOne() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("one", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, nullValue());
		assertThat(alias.properties, nullValue());
	}

	@Test
	void testGetAliasOneWithArgs() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("one", Collections.singletonList("X"),
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
		Settings.Alias alias = AliasUtil.getAlias("two", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("2"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("two", "2"));
	}

	@Test
	void testGetAliasTwoWithArgs() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("two", Collections.singletonList("X"),
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
		Settings.Alias alias = AliasUtil.getAlias("four", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("4"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("four", "4"));
	}

	@Test
	void testGetAliasFourWithArgs() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("four", Collections.singletonList("X"),
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
		Settings.Alias alias = AliasUtil.getAlias("five", null, null);
		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("http://dummy"));
		assertThat(alias.arguments, iterableWithSize(1));
		assertThat(alias.arguments, contains("3"));
		assertThat(alias.properties, aMapWithSize(1));
		assertThat(alias.properties, hasEntry("three", "3"));
	}

	@Test
	void testGetAliasFiveWithArgs() throws IOException {
		Settings.Alias alias = AliasUtil.getAlias("five", Collections.singletonList("X"),
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
			Settings.Alias alias = AliasUtil.getAlias("eight", null, null);
			Assert.fail();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(), containsString("seven"));
		}

	}

}
