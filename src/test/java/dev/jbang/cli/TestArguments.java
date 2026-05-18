package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

class TestArguments extends BaseTest {

	@Test
	public void testBasicArguments() {
		Run run = JBang.parseCommand("run", "-h", "--debug", "myfile.java");

		assertThat(run.runMixin.debugString, hasEntry("address", "4004"));
		assertThat(run.scriptMixin.scriptOrFile, is("myfile.java"));
		assertThat(run.userParams.size(), is(0));
	}

	@Test
	public void testDoubleDebug() {
		Run run = JBang.parseCommand("run", "--debug", "test.java", "--debug", "wonka");

		assertThat(run.runMixin.debugString, hasEntry("address", "4004"));

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.userParams, is(Arrays.asList("--debug", "wonka")));
	}

	@Test
	public void testStdInWithHelpParam() {
		Run run = JBang.parseCommand("run", "-", "--help");

		assertThat(run.scriptMixin.scriptOrFile, is("-"));
		assertThat(run.userParams, is(Collections.singletonList("--help")));
	}

	@Test
	public void testScriptWithHelpParam() {
		Run run = JBang.parseCommand("run", "test.java", "-h");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.userParams, is(Collections.singletonList("-h")));
	}

	@Test
	public void testDebugWithScript() {
		Run run = JBang.parseCommand("run", "--debug", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
	}

	@Test
	public void testDebugPort() {
		Run run = JBang.parseCommand("run", "--debug=*:5000", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "*:5000"));
		assertThat(run.runMixin.debugString.size(), is(1));
	}

	@Test
	public void testDebugPortHost() {
		Run run = JBang.parseCommand("run", "--debug=somehost:5000", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "somehost:5000"));
		assertThat(run.runMixin.debugString.size(), is(1));
	}

	@Test
	public void testDynamicPort() {
		Run run = JBang.parseCommand("run", "--debug=5000?", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "5000?"));
		assertThat(run.runMixin.debugString.size(), is(1));
	}

	@Test
	public void testShortDynamicPort() {
		Run run = JBang.parseCommand("run", "-d=address=5000?", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "5000?"));
		assertThat(run.runMixin.debugString.size(), is(1));
	}

	@Test
	public void testDynamicHostPort() {
		Run run = JBang.parseCommand("run", "--debug=host:5000?", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "host:5000?"));
		assertThat(run.runMixin.debugString.size(), is(1));
	}

	@Test
	public void testAddressDynamicHostPort() {
		Run run = JBang.parseCommand("run", "--debug=host:5000", "--debug=suspend=n", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("suspend", "n"));
		assertThat(run.runMixin.debugString, hasEntry("address", "host:5000"));
		assertThat(run.runMixin.debugString.size(), is(2));
	}

	@Test
	public void testDebugPortSeperateValue() {
		Run run = JBang.parseCommand("run", "--debug", "xyz.dk:5005", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
		assertThat(run.runMixin.debugString, notNullValue());
		assertThat(run.runMixin.debugString, hasEntry("address", "xyz.dk:5005"));
	}

	@Test
	public void testSimpleScript() {
		Run run = JBang.parseCommand("run", "test.java");

		assertThat(run.scriptMixin.scriptOrFile, is("test.java"));
	}

	@Test
	public void testClearCache() {
		Path dir = jbangTempDir;
		environmentVariables.set(Settings.JBANG_CACHE_DIR, dir.toString());
		assertThat(Files.isDirectory(dir), is(true));

		JBang.execute("cache", "clear", "--all");

		assertThat(Files.isDirectory(dir.resolve("urls")), is(false));
		assertThat(Files.isDirectory(dir.resolve("jars")), is(false));
		assertThat(Files.isDirectory(dir.resolve("jdks")), is(false));
		assertThat(Files.notExists(Settings.getCacheDependencyFile()), is(true));
	}

}
