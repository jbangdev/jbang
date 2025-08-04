package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;

public class ExportIT extends BaseIT {

	// Feature: export

	// Scenario: basic export no classpath
	// When command('rm helloworld.jar')
	// When command('jbang export local helloworld.java')
	// Then match err contains "Exported to"
	// Then match err contains "helloworld.jar"
	@Test
	public void testBasicExportNoClasspath() {
		shell("rm helloworld.jar");
		assertThat(shell("jbang export local helloworld.java"))
			.succeeded()
			.errContains("Exported to")
			.errContains("helloworld.jar");
	}

	// Scenario: basic export slim no classpath
	// When command('rm -rf helloworld.jar lib')
	// When command('jbang export portable helloworld.java')
	// Then match err contains "Exported to"
	// Then match err contains "helloworld.jar"
	@Test
	public void testBasicExportSlimNoClasspath() {
		shell("rm -rf helloworld.jar lib");
		assertThat(shell("jbang export portable helloworld.java"))
			.succeeded()
			.errContains("Exported to")
			.errContains("helloworld.jar");
	}

	// Scenario: basic export classpath
	// When command('rm -rf classpath_example.jar lib')
	// When command('jbang export portable classpath_example.java')
	// Then match err contains "Exported to"
	// Then match err contains "classpath_example.jar"
	// When command('jbang export portable --force classpath_example.java')
	// Then match err contains "Exported to"
	// Then match err contains "classpath_example.jar"
	@Test
	public void testBasicExportClasspath() {
		shell("rm -rf classpath_example.jar lib");
		assertThat(shell("jbang export portable classpath_example.java"))
			.succeeded()
			.errContains("Exported to")
			.errContains("classpath_example.jar");
		assertThat(shell("jbang export portable --force classpath_example.java"))
			.succeeded()
			.errContains("Exported to")
			.errContains(
					"classpath_example.jar");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang export --help"))
				.succeeded()
				.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang export"))
				.failed()
				.errContains("Missing required subcommand");
	}
}