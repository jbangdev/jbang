package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestApp extends BaseTest {
	private static final List<String> shContents = Arrays.asList("#!/bin/sh",
			"exec jbang run '$CWD/itests/with space/helloworld.java' \"$@\"");
	private static final List<String> cmdContents = Arrays.asList("@echo off",
			"jbang run ^\"$CWD\\itests\\with^ space\\helloworld.java^\" %*");
	private static final List<String> ps1Contents = Collections.singletonList(
			"jbang run '$CWD\\itests\\with space\\helloworld.java' @args");

	private static final List<String> nativeShContents = Arrays.asList("#!/bin/sh",
			"exec jbang run --native '$CWD/itests/with space/helloworld.java' \"$@\"");
	private static final List<String> nativeCmdContents = Arrays.asList("@echo off",
			"jbang run --native ^\"$CWD\\itests\\with^ space\\helloworld.java^\" %*");
	private static final List<String> nativePs1Contents = Collections.singletonList(
			"jbang run --native '$CWD\\itests\\with space\\helloworld.java' @args");

	private static final List<String> h2shContents = Arrays.asList("#!/bin/sh",
			"exec jbang run com.h2database:h2:1.4.200 \"$@\"");
	private static final List<String> h2cmdContents = Arrays.asList("@echo off",
			"jbang run com.h2database:h2:1.4.200 %*");
	private static final List<String> h2ps1Contents = Collections.singletonList(
			"jbang run com.h2database:h2:1.4.200 @args");

	private static final List<String> aliasShContents = Arrays.asList("@echo off",
			"jbang run myalias %*");
	private static final List<String> aliasCmdContents = Arrays.asList("@echo off",
			"jbang run myalias %*");
	private static final List<String> aliasPs1Contents = Collections.singletonList(
			"jbang run myalias @args");

	@Test
	void testAppInstallFile() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		testScripts();
	}

	@Test
	void testAppInstallWithRepos() throws Exception {
		String src = examplesTestFolder.resolve("repos.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("app", "install", "--no-build", "--fresh", "--force",
					"--repos=https://maven.repository.redhat.com/ga/", src);
		AppInstall app = (AppInstall) pr.subcommand().subcommand().commandSpec().userObject();

		ProjectBuilder pb = app.createProjectBuilder();
		Project prj = pb.build(src);
		assertThat(prj.getRepositories(), hasItem(new MavenRepo("central", "https://repo1.maven.org/maven2/")));
	}

	@Test
	void testAppNativeInstallFile() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "--native", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		testNativeScripts();
	}

	@Test
	void testAppInstallExtensionLessFile() throws Exception {
		String src = examplesTestFolder.resolve("kubectl-example").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: kubectl-example"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}

	}

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void testAppInstallURL() throws Exception {
		CaptureResult result = checkedRun(null, "app", "install", "--no-build",
				"https://github.com/jbangdev/k8s-cli-java/blob/jbang/kubectl-example");
		assertThat(result.err, containsString("Command installed: kubectl-example"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}

	}

	@Test
	void testAppInstallGVA() throws Exception {
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "--name", "h2",
				"com.h2database:h2:1.4.200");
		assertThat(result.err, containsString("Command installed: h2"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}

		String cwd = examplesTestFolder.getParent().toString();
		if (Util.isWindows()) {
			testScript("h2.cmd", cwd, h2cmdContents);
			testScript("h2.ps1", cwd, h2ps1Contents);
			testScript("h2", cwd.replace('\\', '/'), h2shContents);
		} else {
			testScript("h2", cwd, h2shContents);
		}
	}

	@Test
	void testAppInstallFileExists() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--no-build", src);
		assertThat(result.err,
				containsString("A script with name 'helloworld' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileForce() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--no-build", "--force", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		testScripts();
	}

	private void testScripts() throws IOException {
		String cwd = examplesTestFolder.getParent().toString();
		if (Util.isWindows()) {
			testScript("helloworld.cmd", cwd, cmdContents);
			testScript("helloworld.ps1", cwd, ps1Contents);
			testScript("helloworld", cwd.replace('\\', '/'), shContents);
		} else {
			testScript("helloworld", cwd, shContents);
		}
	}

	private void testNativeScripts() throws IOException {
		String cwd = examplesTestFolder.getParent().toString();
		if (Util.isWindows()) {
			testScript("helloworld.cmd", cwd, nativeCmdContents);
			testScript("helloworld.ps1", cwd, nativePs1Contents);
			testScript("helloworld", cwd.replace('\\', '/'), nativeShContents);
		} else {
			testScript("helloworld", cwd, nativeShContents);
		}
	}

	private void testScript(String name, String cwd, List<String> contents) throws IOException {
		List<String> cs = contents.stream()
			.map(l -> l.replace("$CWD", cwd))
			.collect(Collectors.toList());
		Path shFile = Settings.getConfigBinDir().resolve(name);
		assertThat(shFile.toFile(), anExistingFile());
		assertThat(Files.readAllLines(shFile), is(cs));
	}

	@Test
	void testAppInstallFileWithName() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("hello.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("hello.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("hello").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallFileWithNameExists() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err,
				containsString("A script with name 'hello' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileWithNameForce() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun(null, "app", "install", "--no-build", "--force", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
	}

	@Test
	void testAppInstallAlias() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun(null, "alias", "add", "-g", "--name=apptest", src);
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "apptest");
		assertThat(result.err, containsString("Command installed: apptest"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("apptest.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("apptest.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("apptest").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallAliasFromRepo() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun(null, "alias", "add", "-g", "--name=apptest", src);
		checkedRun(null, "catalog", "add", "-g", "--name=testrepo",
				jbangTempDir.resolve("jbang-catalog.json").toString());
		CaptureResult result = checkedRun(null, "app", "install", "--no-build", "apptest@testrepo");
		assertThat(result.err, containsString("Command installed: apptest"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
			assertThat(Settings.getConfigBinDir().resolve("apptest.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("apptest.ps1").toFile(), anExistingFile());
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
			assertThat(Settings.getConfigBinDir().resolve("apptest").toFile(), anExistingFile());
		}
	}

	@Test
	void testAppInstallInvalidName() throws Exception {
		try {
			checkedRun(null, "app", "install", "--no-build", "--name=invalid>name", "def/not/existing/file");
			Assert.fail();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), containsString("Not a valid command name"));
		}
	}

	@Test
	void testAppInstallInvalidRef() throws Exception {
		try {
			checkedRun(null, "app", "install", "--no-build", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(),
					containsString("Script or alias could not be found or read: 'def/not/existing/file'"));
		}
	}

	@Test
	void testAppList() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun(null, "app", "install", "--no-build", "--name=hello1", src);
		checkedRun(null, "app", "install", "--no-build", "--name=hello2", src);
		checkedRun(null, "app", "install", "--no-build", "--name=hello3", src);
		CaptureResult result = checkedRun(null, "app", "list");
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), equalTo("hello1\nhello2\nhello3\n"));
	}

	@Test
	void testAppUninstall() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun(null, "app", "install", "--no-build", src);
		if (Util.isWindows()) {
			assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), anExistingFile());
		} else {
			assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), anExistingFile());
		}
		CaptureResult result = checkedRun(null, "app", "uninstall", "helloworld");
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.err, containsString("Command removed: helloworld"));
		assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), not(anExistingFile()));
	}

	@Test
	void testAppUninstallUnknown() throws Exception {
		CaptureResult result = checkedRun(null, "app", "uninstall", "hello");
		assertThat(result.result, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Command not found: hello"));
	}

}
