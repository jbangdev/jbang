package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.*;

import java.io.IOException;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.Util;

public class TestApp extends BaseTest {

	@Test
	void testAppInstallFile() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "examples/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallFileWithName() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--name=hello", "examples/helloworld.java");
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("hello.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("hello.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("hello").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallAlias() throws IOException {
		checkedRun(null, "alias", "add", "-g", "--name=apptest", "examples/helloworld.java");
		ExecutionResult result = checkedRun(null, "app", "install", "apptest");
		assertThat(result.err, containsString("Command installed: apptest"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("apptest.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("apptest.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("apptest").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallAliasFromRepo() throws IOException {
		checkedRun(null, "alias", "add", "-g", "--name=apptest", "examples/helloworld.java");
		checkedRun(null, "catalog", "add", "-g", "testrepo",
				jbangTempDir.getRoot().toPath().resolve("jbang-catalog.json").toString());
		ExecutionResult result = checkedRun(null, "app", "install", "apptest@testrepo");
		assertThat(result.err, containsString("Command installed: apptest"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("apptest.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("apptest.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("apptest").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallInvalidName() throws IOException {
		try {
			ExecutionResult result = checkedRun(null, "app", "install", "--name=invalid>name", "def/not/existing/file");
			Assert.fail();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), containsString("Not a valid command name"));
		}
	}

	@Test
	void testAppInstallInvalidRef() throws IOException {
		try {
			ExecutionResult result = checkedRun(null, "app", "install", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(), containsString("Could not read script argument"));
		}
	}

	@Test
	void testAppList() throws IOException {
		checkedRun(null, "app", "install", "--name=hello1", "examples/helloworld.java");
		checkedRun(null, "app", "install", "--name=hello2", "examples/helloworld.java");
		checkedRun(null, "app", "install", "--name=hello3", "examples/helloworld.java");
		ExecutionResult result = checkedRun(null, "app", "list");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), equalTo("hello1\nhello2\nhello3\n"));
	}

	@Test
	void testAppUninstall() throws IOException {
		checkedRun(null, "app", "install", "examples/helloworld.java");
		if (Util.isWindows()) {
			assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), anExistingFile());
		} else {
			assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), anExistingFile());
		}
		ExecutionResult result = checkedRun(null, "app", "uninstall", "helloworld");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.err, containsString("Command removed: helloworld"));
		assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), not(anExistingFile()));
	}

	@Test
	void testAppUninstallUnknown() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "uninstall", "hello");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Command not found: hello"));
	}

}
