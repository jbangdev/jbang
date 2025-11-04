package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import dev.jbang.util.Util;

import io.qameta.allure.Description;

public class RunIT extends BaseIT {

	@Test
	@Description("Testing that jbang is running in a clean environment")
	public void testIsolation() {
		assertThat(shell("jbang version --verbose"))
			.errContains("Cache: " + scratch().toString())
			.errContains("Config: " + scratch().toString())
			.errContains(
					"Repository: " + scratch().resolve("itest-m2").toString())
			.succeeded();
	}

	// Scenario: should fail on missing file
	// * command('jbang notthere.java')
	// * match err contains 'Script or alias could not be found or read:
	// \'notthere.java\''
	// * match exit == 2
	@Test
	public void shouldFailOnMissingFile() {
		assertThat(shell("jbang notthere.java"))
			.errContains(
					"Script or alias could not be found or read: 'notthere.java'")
			.exitedWith(2);
	}

	// Scenario: parameter passing
	// * command('jbang helloworld.java jbangtest')
	// * match err == "[jbang] Building jar for helloworld.java...\n"
	// * match out == "Hello jbangtest\n"
	@Test
	public void shouldPassParameters() {
		assertThat(shell("jbang helloworld.java jbangtest"))
			.errEquals("[jbang] Building jar for helloworld.java..."
					+ lineSeparator())
			.outEquals("Hello jbangtest" + lineSeparator());
	}

	// Scenario: std in
	// * command('cat helloworld.java | jbang - jbangtest')
	// * match err == "[jbang] Building jar for helloworld.java...\n"
	// * match out == "Hello jbangtest\n"
	@Test
	public void shouldPassStdIn() {
		String catCmd = Util.isWindows() ? "type" : "cat";
		assertThat(shell(catCmd + " helloworld.java | jbang - jbangtest"))
			.errEquals(
					"[jbang] Building jar for helloworld.java..."
							+ lineSeparator())
			.outEquals("Hello jbangtest" + lineSeparator());
	}

	@Test
	public void shouldLaunchHelloWorldWithJFR() {
		assertThat(shell("jbang --jfr helloworld.java"))
			.outContains(
					"Started recording 1. No limit specified, using maxsize=250MB as default.");
	}

	// Scenario: java run multiple sources
	// * command('jbang --verbose one.java')
	// * match out contains "Two"
	@Test
	public void shouldRunMultipleSources() {
		assertThat(shell("jbang --verbose one.java"))
			.outContains("Two");
	}

	// Scenario: java run multiple matching sources
	// * command('jbang RootOne.java')
	// * match out contains "NestedOne"
	// * match out contains "NestedTwo"
	@Test
	public void shouldRunMultipleMatchingSources() {
		assertThat(shell("jbang RootOne.java"))
			.outContains("NestedOne")
			.outContains("NestedTwo");
	}

	// Scenario: java run multiple sources via cli
	// * command('jbang -s bar/Bar.java foo.java')
	// * match out contains "Bar"
	@Test
	public void shouldRunMultipleSourcesViaCli() {
		assertThat(shell("jbang -s bar/Bar.java foo.java"))
			.outContains("Bar");
	}

	// Scenario: java run multiple files
	// * command('jbang res/resource.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFiles() {
		assertThat(shell("jbang res/resource.java"))
			.outContains("hello properties");
	}

	// Scenario: java run multiple files using globbing
	// * command('jbang resources.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingGlobbing() {
		assertThat(shell("jbang resources.java"))
			.outContains("hello properties");
	}

	// Scenario: java run multiple files using globbing and a mounting folder
	// * command('jbang resourcesmnt.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingGlobbingAndMountingFolder() {
		assertThat(shell("jbang resourcesmnt.java"))
			.outContains("hello properties");
	}

	// Scenario: java run multiple files using alias
	// * command('jbang resource')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingAlias() {
		assertThat(shell("jbang resource"))
			.outContains("hello properties");
	}

	// Scenario: java run multiple files using alias

//Scenario: java run multiple files using remote alias
//Then command('jbang trust add https://raw.githubusercontent.com')
//Then command('jbang resource@test')
//Then match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingRemoteAlias() {
		assertThat(shell("jbang trust add https://raw.githubusercontent.com")).succeeded();
		assertThat(shell("jbang resource@test"))
			.outContains("hello properties");
	}

	@Test
	public void shouldReturnCorrectErrorCodeForJShellExit() {
		assumeTrue(testJavaMajorVersion >= 9, "Piping code via stdin requires JShell which is not supported on Java 8");
		assertThat(shell("jbang run -c \"System.exit(42)\"")).exitedWith(42);
	}
}
