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

public class TestConfig extends BaseTest {

	private static final int SUCCESS_EXIT = BaseCommand.EXIT_OK;

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
	void initEach() throws IOException {
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
	void testList() throws Exception {
		CaptureResult<Integer> result = checkedRun("config", "list");
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("format = text\n" +
						"init.template = hello\n" +
						"one = footop\n" +
						"run.debug = 4004\n" +
						"run.jfr = filename={baseName}.jfr\n" +
						"three = baz\n" +
						"two = bar\n" +
						"wrapper.install.dir = .\n"));
	}

	@Test
	void testGetLocal() throws Exception {
		CaptureResult<Integer> result = checkedRun("config", "get", "two");
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("bar\n"));
	}

	@Test
	void testGetGlobal() throws Exception {
		CaptureResult<Integer> result = checkedRun("config", "get", "run.debug");
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("4004\n"));
	}

	@Test
	void testSetLocal() throws Exception {
		assertThat(Configuration.read(testConfigFile).keySet(), not(hasItem("mykey")));
		checkedRun("config", "set", "mykey=myvalue");
		assertThat(Configuration.read(testConfigFile).keySet(), hasItem("mykey"));
	}

	@Test
	void testSetGlobal() throws Exception {
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("mykey")));
		checkedRun("config", "set", "--global", "mykey=myvalue");
		assertThat(Configuration.read(configFile).keySet(), hasItem("mykey"));
	}

	@Test
	void testSetBuiltin() throws Exception {
		Files.deleteIfExists(testConfigFile);
		Files.deleteIfExists(testConfigFileSub);
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("run.debug")));
		checkedRun("config", "set", "run.debug=42");
		assertThat(Configuration.read(configFile).keySet(), hasItem("run.debug"));
	}

	@Test
	void testUnsetLocal() throws Exception {
		assertThat(Configuration.read(testConfigFile).keySet(), hasItem("two"));
		checkedRun("config", "unset", "two");
		assertThat(Configuration.read(testConfigFile).keySet(), not(hasItem("two")));
	}

	@Test
	void testUnsetGlobal() throws Exception {
		checkedRun("config", "set", "--global", "mykey=myvalue");
		assertThat(Configuration.read(configFile).keySet(), hasItem("mykey"));
		checkedRun("config", "unset", "--global", "mykey");
		assertThat(Configuration.read(configFile).keySet(), not(hasItem("mykey")));
	}

	@Test
	void testUnsetBuiltin() throws Exception {
		CaptureResult<Integer> result = checkedRun("config", "unset", "run.debug");
		assertThat(result.normalizedErr(), containsString("Cannot remove built-in option"));
	}
}
