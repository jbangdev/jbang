package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.Settings;

import picocli.CommandLine;

class TestArguments {

	private CommandLine cli;
	private Jbang jbang;

	@BeforeEach
	void setup() throws IOException {
		cli = Jbang.getCommandLine();
		jbang = cli.getCommand();
		jbangTempDir.create();
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();

	@Test
	public void testHelpSections() {
		Jbang.getCommandRenderer().validate(Jbang.getCommandLine().getHelp());
	}

	@Test
	public void testBasicArguments() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "-h", "--debug", "myfile.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert run.helpRequested;
		assertThat(run.debug(), is(true));
		assertThat(run.scriptOrFile, is("myfile.java"));
		assertThat(run.userParams.size(), is(0));

	}

	@Test
	public void testDoubleDebug() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java", "--debug", "wonka");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.debug(), is(true));
		assertThat(run.debugString, is("4004"));

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.userParams, is(Arrays.asList("--debug", "wonka")));
	}

	/**
	 * @Test public void testInit() { cli.parseArgs("--init", "x.java", "y.java");
	 *       assertThat(main.script, is("x.java")); assertThat(main.params,
	 *       is(Arrays.asList("x.java", "y.java"))); }
	 **/

	@Test
	public void testStdInWithHelpParam() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "-", "--help");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("-"));
		assertThat(run.helpRequested, is(false));
		assertThat(run.userParams, is(Arrays.asList("--help")));
	}

	@Test
	public void testScriptWithHelpParam() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java", "-h");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.helpRequested, is(false));
		assertThat(run.userParams, is(Arrays.asList("-h")));
	}

	@Test
	public void testDebugWithScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
	}

	@Test
	public void testDebugPort() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug=*:5000", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
		assertThat(run.debugString, is("*:5000"));
	}

	@Test
	public void testDebugPortSeperateValue() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "xyz.dk:5005", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
		assertThat(run.debugString, is("xyz.dk:5005"));
	}

	@Test
	public void testSimpleScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
	}

	@Test
	public void testClearCache() {
		Path dir = jbangTempDir.getRoot().toPath();
		environmentVariables.set(Settings.JBANG_CACHE_DIR, dir.toString());
		assertThat(Files.isDirectory(dir), is(true));

		cli.execute("cache", "clear", "--all");

		assertThat(Files.isDirectory(dir.resolve("urls")), is(false));
		assertThat(Files.isDirectory(dir.resolve("jars")), is(false));
		assertThat(Files.isDirectory(dir.resolve("jdks")), is(false));
	}

}
