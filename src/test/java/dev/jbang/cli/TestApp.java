package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.util.Util;

public class TestApp extends BaseTest {
	private static final List<String> shContents = Arrays.asList("#!/bin/sh",
			"eval \"exec jbang run $CWD/itests/helloworld.java $*\"");
	private static final List<String> cmdContents = Arrays.asList("@echo off",
			"jbang run $CWD/itests/helloworld.java %*");
	private static final List<String> ps1Contents = Collections.singletonList(
			"jbang run $CWD/itests/helloworld.java $args");

	private static final List<String> nativeShContents = Arrays.asList("#!/bin/sh",
			"eval \"exec jbang run --native $CWD/itests/helloworld.java $*\"");
	private static final List<String> nativeCmdContents = Arrays.asList("@echo off",
			"jbang run --native $CWD/itests/helloworld.java %*");
	private static final List<String> nativePs1Contents = Collections.singletonList(
			"jbang run --native $CWD/itests/helloworld.java $args");

	private static final List<String> h2shContents = Arrays.asList("#!/bin/sh",
			"eval \"exec jbang run com.h2database:h2:1.4.200 $*\"");
	private static final List<String> h2cmdContents = Arrays.asList("@echo off",
			"jbang run com.h2database:h2:1.4.200 %*");
	private static final List<String> h2ps1Contents = Collections.singletonList(
			"jbang run com.h2database:h2:1.4.200 $args");

	private static final List<String> h2nativeShContents = Arrays.asList("#!/bin/sh",
			"eval \"exec jbang run --native com.h2database:h2:1.4.200 $*\"");
	private static final List<String> h2nativeCmdContents = Arrays.asList("@echo off",
			"jbang run --native com.h2database:h2:1.4.200 %*");
	private static final List<String> h2nativePs1Contents = Collections.singletonList(
			"jbang run --native com.h2database:h2:1.4.200 $args");

	@Test
	void testAppInstallFile() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		testScripts();
	}

	@Test
	void testAppNativeInstallFile() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--native", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		testNativeScripts();
	}

	@Test
	void testAppInstallExtensionLessFile() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "itests/kubectl-example");
		assertThat(result.err, containsString("Command installed: kubectl-example"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}

	}

	@Test
	void testAppInstallURL() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install",
				"https://github.com/jbangdev/k8s-cli-java/blob/jbang/kubectl-example");
		assertThat(result.err, containsString("Command installed: kubectl-example"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}

	}

	@Test
	void testAppInstallGVA() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--name", "h2",
				"com.h2database:h2:1.4.200");
		assertThat(result.err, containsString("Command installed: h2"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}

		if (Util.isWindows()) {
			testScript("h2.cmd", h2cmdContents);
			testScript("h2.ps1", h2ps1Contents);
		} else {
			testScript("h2", h2shContents);
		}
	}

	@Test
	void testAppInstallFileExists() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "itests/helloworld.java");
		assertThat(result.err,
				containsString("A script with name 'helloworld' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileForce() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--force", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: helloworld"));
		testScripts();
	}

	private void testScripts() throws IOException {
		if (Util.isWindows()) {
			testScript("helloworld.cmd", cmdContents);
			testScript("helloworld.ps1", ps1Contents);
		} else {
			testScript("helloworld", shContents);
		}
	}

	private void testNativeScripts() throws IOException {
		if (Util.isWindows()) {
			testScript("helloworld.cmd", nativeCmdContents);
			testScript("helloworld.ps1", nativePs1Contents);
		} else {
			testScript("helloworld", nativeShContents);
		}
	}

	private void testScript(String name, List<String> contents) throws IOException {
		String cwd = Util.getCwd().toString();
		List<String> cs = contents	.stream()
									.map(l -> l.replace('/', File.separatorChar).replace("$CWD", cwd))
									.collect(Collectors.toList());
		Path shFile = Settings.getConfigBinDir().resolve(name);
		assertThat(shFile.toFile(), anExistingFile());
		assertThat(Files.readAllLines(shFile), is(cs));
	}

	@Test
	void testAppInstallFileWithName() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--name=hello", "itests/helloworld.java");
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
	void testAppInstallFileWithNameExists() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--name=hello", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--name=hello", "itests/helloworld.java");
		assertThat(result.err,
				containsString("A script with name 'hello' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileWithNameForce() throws IOException {
		ExecutionResult result = checkedRun(null, "app", "install", "--name=hello", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--force", "--name=hello", "itests/helloworld.java");
		assertThat(result.err, containsString("Command installed: hello"));
	}

	@Test
	void testAppInstallAlias() throws IOException {
		checkedRun(null, "alias", "add", "-g", "--name=apptest", "itests/helloworld.java");
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
		checkedRun(null, "alias", "add", "-g", "--name=apptest", "itests/helloworld.java");
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
			checkedRun(null, "app", "install", "--name=invalid>name", "def/not/existing/file");
			Assert.fail();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), containsString("Not a valid command name"));
		}
	}

	@Test
	void testAppInstallInvalidRef() throws IOException {
		try {
			checkedRun(null, "app", "install", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(), containsString("Could not read script argument"));
		}
	}

	@Test
	void testAppList() throws IOException {
		checkedRun(null, "app", "install", "--name=hello1", "itests/helloworld.java");
		checkedRun(null, "app", "install", "--name=hello2", "itests/helloworld.java");
		checkedRun(null, "app", "install", "--name=hello3", "itests/helloworld.java");
		ExecutionResult result = checkedRun(null, "app", "list");
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), equalTo("hello1\nhello2\nhello3\n"));
	}

	@Test
	void testAppUninstall() throws IOException {
		checkedRun(null, "app", "install", "itests/helloworld.java");
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
