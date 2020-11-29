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
	void testAppInstall() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "hello", "examples/helloworld.java");
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
	void testAppInstallInvalidName() throws IOException {
		try {
			ExecutionResult result = checkedRun(null, "app", "install", "invalid>name", "def/not/existing/file");
			Assert.fail();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), containsString("Not a valid command name"));
		}
	}

	@Test
	void testAppInstallInvalidRef() throws IOException {
		try {
			ExecutionResult result = checkedRun(null, "app", "install", "hello", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(), containsString("Could not read script argument"));
		}
	}

	@Test
	void testAppList() throws IOException {
		checkedRun(null, "app", "install", "hello1", "examples/helloworld.java");
		checkedRun(null, "app", "install", "hello2", "examples/helloworld.java");
		checkedRun(null, "app", "install", "hello3", "examples/helloworld.java");
		ExecutionResult result = checkedRun(null, "app", "list");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), equalTo("hello1\nhello2\nhello3\n"));
	}

	@Test
	void testAppUninstall() throws IOException {
		checkedRun(null, "app", "install", "hello", "examples/helloworld.java");
		if (Util.isWindows()) {
			assertThat(Settings.getConfigBinDir().resolve("hello.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("hello.ps1").toFile(), anExistingFile());
		} else {
			assertThat(Settings.getConfigBinDir().resolve("hello").toFile(), anExistingFile());
		}
		ExecutionResult result = checkedRun(null, "app", "uninstall", "hello");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.err, containsString("Command removed: hello"));
		assertThat(Settings.getConfigBinDir().resolve("hello").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("hello.cmd").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("hello.ps1").toFile(), not(anExistingFile()));
	}

	@Test
	void testAppUninstallUnknown() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "uninstall", "hello");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Command not found: hello"));
	}

}
