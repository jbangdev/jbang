package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class InitIT extends BaseIT {

	// Feature: init

	// Scenario: init of file and run
	// When command('jbang init ' + scratch + '/test.java')
	// * match exit == 0
	// * def contents = read(scratch + '/test.java')
	// * match contents contains "class test"
	// * command('jbang ' + scratch + '/test.java')
	// * match err == "[jbang] Building jar for test.java...\n"
	// * command('jbang ' + scratch + '/test.java')
	// * match err !contains "[jbang] Building jar"
	@Test
	public void testInitAndRun() throws IOException {
		Path testFile = scratch().resolve("test.java");
		assertThat(shell("jbang init " + testFile))
			.succeeded();

		String contents = new String(Files.readAllBytes(testFile));

		assertThat(contents).satisfiesAnyOf(
				item -> item.contains("class test"), // using java < 25
				item -> item.contains("IO.println(") // using java >= 25 especially nativeimage
		);

		assertThat(shell("jbang " + testFile))
			.succeeded()
			.errContains("[jbang] Building jar for test.java..." + lineSeparator());

		assertThat(shell("jbang " + testFile))
			.succeeded()
			.errNotContains("[jbang] Building jar");
	}

	// Scenario: init of file in a non existing directory
	// When command('jbang init ' + scratch + '/newfolder/test.java')
	// * match exit == 0
	// * def contents = read(scratch + '/newfolder/test.java')
	// * match contents contains "class test"
	// * command('jbang ' + scratch + '/newfolder/test.java')
	// * match err == "[jbang] Building jar for test.java...\n"
	// * command('jbang ' + scratch + '/newfolder/test.java')
	// * match err !contains "[jbang] Building jar"
	@Test
	public void testInitInNewDirectory() throws IOException {
		Path testFile = scratch().resolve("testfolder/test.java");
		assertThat(shell("jbang init " + testFile))
			.succeeded();

		String contents = new String(Files.readAllBytes(testFile));

		assertThat(contents).satisfiesAnyOf(
				item -> item.contains("class test"), // using java < 25
				item -> item.contains("IO.println(") // using java >= 25 especially nativeimage
		);

		// TODO: this is to avoid the file being cached.
		contents = contents + "// " + System.currentTimeMillis();
		Files.write(testFile, contents.getBytes());

		assertThat(shell("jbang " + testFile))
			.succeeded()
			.errEquals("[jbang] Building jar for test.java..." + lineSeparator());

		assertThat(shell("jbang " + testFile))
			.succeeded()
			.errNotContains("[jbang] Building jar");
	}

	// Scenario: init with several file-refs template should display a message
	// regarding the first file using {basename}
	// When command('jbang template add --name test
	// "{basename}Test.java=templates/test.java.qute"
	// "{basename}SecondTest.java=templates/test.java.qute"')
	// Then command('jbang init -t test ' + scratch + '/Script.java')
	// * match exit == 0
	// * fileexist(scratch + '/ScriptTest.java')
	// * fileexist(scratch + '/ScriptSecondTest.java')
	// * match err contains "File initialized. You can now run it with"
	// * match err contains "or edit it using"
	// * match err contains "ScriptTest.java"
	// * match err !contains "Script.java"
	// * match err !contains "ScriptSecondTest.java"
	@Test
	public void testInitWithTemplate() throws IOException {
		// TODO: --force to avoid test created in other test
		assertThat(shell(
				"jbang template add --force --name test \"{basename}Test.java=templates/test.java.qute\" \"{basename}SecondTest.java=templates/test.java.qute\""))
			.succeeded();

		Path scriptFile = scratch().resolve("Script.java");
		assertThat(shell("jbang init -t test " + scriptFile))
			.succeeded()
			.errContains(
					"File initialized. You can now run it with")
			.errContains("or edit it using")
			.errContains("ScriptTest.java")
			.errNotContains("Script.java")
			.errNotContains("ScriptSecondTest.java");

		assertThat(Files.exists(scratch().resolve("ScriptTest.java"))).isTrue();
		assertThat(Files.exists(scratch().resolve("ScriptSecondTest.java"))).isTrue();
	}

	@Test
	public void testInitWithJavaVersion() throws IOException {
		assertThat(shell("jbang init --java 25 " + scratch().resolve("j25.java")))
			.errContains("File initialized. You can now run it with")
			.succeeded();

		assertThat(Files.readString(scratch().resolve("j25.java")))
			.containsPattern("(?m)^void main\\(String\\.\\.\\. args\\) \\{");
	}
}