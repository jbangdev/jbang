package dk.xam.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFileOrDirectory;

import java.io.IOException;
import java.util.Arrays;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

class TestArguments {

	private CommandLine cli;
	private Jbang jbang;

	@BeforeEach
	void setup() {
		cli = Jbang.getCommandLine();
		jbang = cli.getCommand();
	}

	@Test
	public void testBasicArguments() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "-h", "--debug", "myfile.java");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assert run.helpRequested;
		assertThat(run.debug(), is(true));
		assertThat(run.scriptOrFile, is("myfile.java"));
		assertThat(run.userParams.size(), is(0));

	}

	@Test
	public void testDoubleDebug() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java", "--debug", "wonka");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.debug(), is(true));
		assertThat(run.debugPort, is(4004));

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
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("-"));
		assertThat(run.helpRequested, is(false));
		assertThat(run.userParams, is(Arrays.asList("--help")));
	}

	@Test
	public void testScriptWithHelpParam() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java", "-h");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.helpRequested, is(false));
		assertThat(run.userParams, is(Arrays.asList("-h")));
	}

	@Test
	public void testDebugWithScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
	}

	@Test
	public void testDebugPort() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug=5000", "test.java");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
		assertThat(run.debugPort, is(5000));
	}

	@Test
	public void testDebugPortSeperateValue() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "5005", "test.java");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
		assertThat(run.debug(), is(true));
		assertThat(run.debugPort, is(5005));
	}

	@Test
	public void testSimpleScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java");
		JbangRun run = (JbangRun) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptOrFile, is("test.java"));
	}

	@Test
	public void testClearCache() throws IOException {
		CommandLine.ParseResult pr = cli.parseArgs("cache", "clear");
		JbangCacheClear cc = (JbangCacheClear) pr.subcommand().subcommand().commandSpec().userObject();

		cc.call();
		MatcherAssert.assertThat(Settings.getCacheDir(false).toFile(), not(anExistingFileOrDirectory()));
		assertThat(Settings.getCacheDir(false).toFile().listFiles(), nullValue());
	}

}
