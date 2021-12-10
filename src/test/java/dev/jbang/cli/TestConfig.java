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
import dev.jbang.Configuration;

import picocli.CommandLine;

public class TestConfig extends BaseTest {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	static final String testConfig = "" +
			"one=foo\n" +
			"two=bar\n" +
			"three=baz\n";

	static Path configFile = null;
	static Path testConfigFile = null;

	@BeforeEach
	void init() throws IOException {
		configFile = jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		testConfigFile = cwdDir.resolve("jbang.properties");
		Files.write(testConfigFile, testConfig.getBytes());
		clearSettingsCaches();
	}

	@Test
	void testList() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "list");
		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("init.template = hello\n" +
						"one = foo\n" +
						"run.debug = 4004\n" +
						"run.jfr = filename={baseName}.jfr\n" +
						"three = baz\n" +
						"two = bar\n" +
						"wrapper.dir = .\n"));
	}

	@Test
	void testGetLocal() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "get", "two");
		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("bar\n"));
	}

	@Test
	void testGetGlobal() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "get", "run.debug");
		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("4004\n"));
	}

	@Test
	void testSetLocal() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "set", "mykey", "myvalue");
		assertThat(Configuration.read(testConfigFile).keySet(), hasItem("mykey"));
	}

	@Test
	void testSetGlobal() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "set", "--global", "mykey", "myvalue");
		assertThat(Configuration.read(configFile).keySet(), hasItem("mykey"));
	}
}
