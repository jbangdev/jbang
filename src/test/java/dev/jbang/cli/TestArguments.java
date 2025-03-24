package dev.jbang.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

import picocli.CommandLine;

class TestArguments extends BaseTest {

	private CommandLine cli;

	@BeforeEach
	void setup() {
		cli = JBang.getCommandLine();
	}

	@Test
	public void testHelpSections() {
		JBang.getCommandRenderer().validate(JBang.getCommandLine().getHelp());
	}

	@Test
	public void testBasicArguments() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "-h", "--debug", "myfile.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert run.helpRequested;
		assertThat(run.runMixin.debugString).containsEntry("address", "4004");
		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("myfile.java");
		assertThat(run.userParams.size()).isEqualTo(0);

	}

	@Test
	public void testDoubleDebug() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java", "--debug", "wonka");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.runMixin.debugString).containsEntry("address", "4004");

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
		assertThat(run.userParams).isEqualTo(Arrays.asList("--debug", "wonka"));
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

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("-");
		assertThat(run.helpRequested).isEqualTo(false);
		assertThat(run.userParams).isEqualTo(Collections.singletonList("--help"));
	}

	@Test
	public void testScriptWithHelpParam() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java", "-h");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
		assertThat(run.helpRequested).isEqualTo(false);
		assertThat(run.userParams).isEqualTo(Collections.singletonList("-h"));
	}

	@Test
	public void testDebugWithScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
		assertThat(run.runMixin.debugString).isNotNull();
	}

	@Test
	public void testDebugPort() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug=*:5000", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
		assertThat(run.runMixin.debugString).isNotNull();
		assertThat(run.runMixin.debugString).containsEntry("address", "*:5000");
		assertThat(run.runMixin.debugString.size()).isEqualTo(1);
	}

	@Test
	public void testDebugPortSeperateValue() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "--debug", "xyz.dk:5005", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
		assertThat(run.runMixin.debugString).isNotNull();
		assertThat(run.runMixin.debugString).containsEntry("address", "xyz.dk:5005");
	}

	@Test
	public void testSimpleScript() {
		CommandLine.ParseResult pr = cli.parseArgs("run", "test.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.scriptMixin.scriptOrFile).isEqualTo("test.java");
	}

	@Test
	public void testClearCache() {
		Path dir = jbangTempDir;
		environmentVariables.set(Settings.JBANG_CACHE_DIR, dir.toString());
		assertThat(Files.isDirectory(dir)).isEqualTo(true);

		cli.execute("cache", "clear", "--all");

		assertThat(Files.isDirectory(dir.resolve("urls"))).isEqualTo(false);
		assertThat(Files.isDirectory(dir.resolve("jars"))).isEqualTo(false);
		assertThat(Files.isDirectory(dir.resolve("jdks"))).isEqualTo(false);
		assertThat(Files.notExists(Settings.getCacheDependencyFile())).isEqualTo(true);
	}

}
