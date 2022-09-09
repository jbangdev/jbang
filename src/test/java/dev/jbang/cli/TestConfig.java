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
			"one=footop\n" +
			"two=bar\n";

	static final String testConfigSub = "" +
			"one=foosub\n" +
			"three=baz\n";

	static Path configFile = null;
	static Path testConfigFile = null;
	static Path testConfigFileSub = null;

	@BeforeEach
	void init() throws IOException {
		configFile = jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		testConfigFile = cwdDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		Files.write(testConfigFile, testConfig.getBytes());
		Path testSubDir = cwdDir.resolve(".jbang");
		testSubDir.toFile().mkdir();
		testConfigFileSub = testSubDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		Files.write(testConfigFileSub, testConfigSub.getBytes());
		clearSettingsCaches();
	}

	@Test
	void testList() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "list");
		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("init.template = hello\n" +
						"one = footop\n" +
						"run.debug = 4004\n" +
						"run.jfr = filename={baseName}.jfr\n" +
						"three = baz\n" +
						"two = bar\n" +
						"wrapper.install.dir = .\n"));
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
		assertThat(Configuration.read(testConfigFile).keySet(), not(hasItem("mykey")));
		ExecutionResult result = checkedRun(null, "config", "set", "mykey", "myvalue");
		assertThat(Configuration.read(testConfigFile).keySet(), hasItem("mykey"));
	}

	@Test
	void testSetGlobal() throws IOException {
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("mykey")));
		ExecutionResult result = checkedRun(null, "config", "set", "--global", "mykey", "myvalue");
		assertThat(Configuration.read(configFile).keySet(), hasItem("mykey"));
	}

	@Test
	void testSetBuiltin() throws IOException {
		Files.deleteIfExists(testConfigFile);
		Files.deleteIfExists(testConfigFileSub);
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("run.debug")));
		ExecutionResult result = checkedRun(null, "config", "set", "run.debug", "42");
		assertThat(Configuration.read(configFile).keySet(), hasItem("run.debug"));
	}

	@Test
	void testUnsetLocal() throws IOException {
		assertThat(Configuration.read(testConfigFile).keySet(), hasItem("two"));
		ExecutionResult result = checkedRun(null, "config", "unset", "two");
		assertThat(Configuration.read(testConfigFile).keySet(), not(hasItem("two")));
	}

	@Test
	void testUnsetGlobal() throws IOException {
		checkedRun(null, "config", "set", "--global", "mykey", "myvalue");
		assertThat(Configuration.read(configFile).keySet(), hasItem("mykey"));
		ExecutionResult result = checkedRun(null, "config", "unset", "--global", "mykey");
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("mykey")));
	}

	@Test
	void testUnsetBuiltin() throws IOException {
		ExecutionResult result = checkedRun(null, "config", "unset", "run.debug");
		assertThat(result.normalizedErr(), containsString("Cannot remove built-in option"));
	}
}
