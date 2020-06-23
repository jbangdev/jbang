package dk.xam.jbang;

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
		Settings.aliasInfo = null;
		assertThat(Settings.getAliases().get("one"), notNullValue());
	}

	@Test
	void testAdd() throws IOException {
		Main main = new Main();
		new CommandLine(main).execute("alias", "add", "new", "http://dummy");
		Settings.aliasInfo = null;
		assertThat(Settings.getAliases().get("new").scriptRef, equalTo("http://dummy"));
	}

	@Test
	void testRemove() throws IOException {
		Settings.aliasInfo = null;
		System.err.println(Settings.getAliases().toString());
		assertThat(Settings.getAliases().get("two"), notNullValue());
		Main main = new Main();
		new CommandLine(main).execute("alias", "remove", "two");
		Settings.aliasInfo = null;
		assertThat(Settings.getAliases().get("two"), nullValue());
	}

}
