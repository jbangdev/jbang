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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.Util;

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
			"jbang run 'com.h2database:h2:1.4.200' @args");

	@Test
	void testHasJBangSetup(@TempDir Path tempDir) throws IOException {
		Path rcFile = tempDir.resolve(".bashrc");
		assertThat(App.AppSetup.hasJBangSetup(rcFile), is(false));

		Files.write(rcFile, Collections.singletonList("export PATH=/usr/bin"));
		assertThat(App.AppSetup.hasJBangSetup(rcFile), is(false));

		Files.write(rcFile, Collections.singletonList("# Add JBang to environment"));
		assertThat(App.AppSetup.hasJBangSetup(rcFile), is(true));
	}

	@Test
	void testActiveWindowsCmdLauncher() {
		Assumptions.assumeTrue(Util.isWindows());
		Path launcher = jbangTempDir.resolve("bin").resolve("jbang.cmd");
		environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "cmd");
		environmentVariables.set(Util.JBANG_LAUNCH_CMD, launcher.toString());

		assertThat(App.AppInstall.isActiveCmdLauncher(launcher), is(true));
		assertThat(App.AppInstall.isActiveCmdLauncher(launcher.resolveSibling("jbang.ps1")), is(false));
	}

	@Test
	void testInactiveWindowsCmdLauncher() {
		Assumptions.assumeTrue(Util.isWindows());
		Path launcher = jbangTempDir.resolve("bin").resolve("jbang.cmd");
		environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
		environmentVariables.set(Util.JBANG_LAUNCH_CMD, launcher.toString());

		assertThat(App.AppInstall.isActiveCmdLauncher(launcher), is(false));
	}

	@Test
	void testUpdateStagesActiveWindowsCmdLauncher(@TempDir Path tempDir) throws IOException, InterruptedException {
		Assumptions.assumeTrue(Util.isWindows());
		Path source = Files.createDirectory(tempDir.resolve("source"));
		Path target = Files.createDirectory(tempDir.resolve("target"));
		Files.writeString(source.resolve("jbang"), "new sh");
		Files.writeString(source.resolve("jbang.cmd"), "new cmd");
		Files.writeString(source.resolve("jbang.ps1"), "new ps1");
		Files.writeString(source.resolve("jbang.jar"), "new jar");
		Files.writeString(target.resolve("jbang.cmd"), "running cmd");
		Files.writeString(target.resolve("jbang.jar"), "running jar");
		environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "cmd");
		environmentVariables.set(Util.JBANG_LAUNCH_CMD, target.resolve("jbang.cmd").toString());

		App.AppInstall.copyJBangFiles(source, target);

		assertThat(Files.readString(target.resolve("jbang.cmd")), is("running cmd"));
		assertThat(Files.readString(target.resolve("jbang.cmd.new")), is("new cmd"));
		assertThat(Files.readString(target.resolve("jbang")), is("new sh"));
		assertThat(Files.readString(target.resolve("jbang.ps1")), is("new ps1"));
		assertThat(Files.readString(target.resolve("jbang.jar")), is("running jar"));
		assertThat(Files.readString(target.resolve("jbang.jar.new")), is("new jar"));

		String updateCommand = App.AppInstall.cmdLauncherUpdateCommand();
		assertThat(updateCommand, notNullValue());
		Process process = new ProcessBuilder("cmd", "/d", "/c", updateCommand).start();
		assertThat(process.waitFor(), is(0));
		assertThat(Files.readString(target.resolve("jbang.cmd")), is("new cmd"));
		assertThat(target.resolve("jbang.cmd.new").toFile(), not(anExistingFile()));
	}

	@Test
	void testAppInstallFile() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", src);
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
		App.AppInstall app = JBang.parseCommand("app", "install", "--no-build", "--fresh", "--force",
				"--repos=https://maven.repository.redhat.com/ga/", src);

		ProjectBuilder pb = app.createBaseProjectBuilder();
		Project prj = pb.build(src);
		assertThat(prj.getRepositories(), hasItem(new MavenRepo("central", "https://repo1.maven.org/maven2/")));
	}

	@Test
	void testAppNativeInstallFile() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--native", src);
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
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", src);
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
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build",
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
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--name", "h2",
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
	void testAppInstallWithSystemProperty() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--force",
				"--name=helloprops", "-Djavafx.preview=true", "-Dcamel.jbang.version=4.21.0-SNAPSHOT", src);
		assertThat(result.err, containsString("Command installed: helloprops"));

		String cwd = examplesTestFolder.getParent().toString();
		Path shFile = Settings.getConfigBinDir().resolve(Util.isWindows() ? "helloprops.cmd" : "helloprops");
		String content = String.join("\n", Files.readAllLines(shFile));
		// -D must be joined with the property key (no space)
		assertThat(content, containsString("-Djavafx.preview=true"));
		assertThat(content, containsString("-Dcamel.jbang.version=4.21.0-SNAPSHOT"));
		assertThat(content, not(containsString("-D javafx.preview")));
		assertThat(content, not(containsString("-D camel.jbang.version")));

		if (Util.isWindows()) {
			// Also check ps1 script
			Path ps1File = Settings.getConfigBinDir().resolve("helloprops.ps1");
			String ps1Content = String.join("\n", Files.readAllLines(ps1File));
			assertThat(ps1Content, containsString("-Djavafx.preview=true"));
			assertThat(ps1Content, containsString("-Dcamel.jbang.version=4.21.0-SNAPSHOT"));
			assertThat(ps1Content, not(containsString("-D javafx.preview")));
			assertThat(ps1Content, not(containsString("-D camel.jbang.version")));
		}
	}

	@Test
	void testAppInstallFileExists() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun("app", "install", "--no-build", src);
		assertThat(result.err,
				containsString("A script with name 'helloworld' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileForce() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", src);
		assertThat(result.err, containsString("Command installed: helloworld"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun("app", "install", "--no-build", "--force", src);
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
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--name=hello", src);
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
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun("app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err,
				containsString("A script with name 'hello' already exists, use '--force' to install anyway."));
	}

	@Test
	void testAppInstallFileWithNameForce() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
		if (Util.isWindows()) {
			assertThat(result.result, equalTo(BaseCommand.EXIT_EXECUTE));
		} else {
			assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		}
		result = checkedRun("app", "install", "--no-build", "--force", "--name=hello", src);
		assertThat(result.err, containsString("Command installed: hello"));
	}

	@Test
	void testAppInstallAlias() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun("alias", "add", "-g", "--name=apptest", src);
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "apptest");
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
		checkedRun("alias", "add", "-g", "--name=apptest", src);
		checkedRun("catalog", "add", "-g", "--name=testrepo",
				jbangTempDir.resolve("jbang-catalog.json").toString());
		CaptureResult<Integer> result = checkedRun("app", "install", "--no-build", "apptest@testrepo");
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
			checkedRun("app", "install", "--no-build", "--name=invalid>name", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(), containsString("Not a valid command name"));
		}
	}

	@Test
	void testAppInstallInvalidRef() throws Exception {
		try {
			checkedRun("app", "install", "--no-build", "def/not/existing/file");
			Assert.fail();
		} catch (ExitException e) {
			assertThat(e.getMessage(),
					containsString("Script or alias could not be found or read: 'def/not/existing/file'"));
		}
	}

	@Test
	void testAppList() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun("app", "install", "--no-build", "--name=hello1", src);
		checkedRun("app", "install", "--no-build", "--name=hello2", src);
		checkedRun("app", "install", "--no-build", "--name=hello3", src);
		CaptureResult<Integer> result = checkedRun("app", "list");
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), equalTo("hello1\nhello2\nhello3\n"));
	}

	@Test
	void testAppUninstall() throws Exception {
		String src = examplesTestFolder.resolve("with space/helloworld.java").toString();
		checkedRun("app", "install", "--no-build", src);
		if (Util.isWindows()) {
			assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), anExistingFile());
			assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), anExistingFile());
		} else {
			assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), anExistingFile());
		}
		CaptureResult<Integer> result = checkedRun("app", "uninstall", "helloworld");
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		assertThat(result.err, containsString("Command removed: helloworld"));
		assertThat(Settings.getConfigBinDir().resolve("helloworld").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.cmd").toFile(), not(anExistingFile()));
		assertThat(Settings.getConfigBinDir().resolve("helloworld.ps1").toFile(), not(anExistingFile()));
	}

	@Test
	void testAppUninstallUnknown() throws Exception {
		CaptureResult<Integer> result = checkedRun("app", "uninstall", "hello");
		assertThat(result.result, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Command not found: hello"));
	}

}
