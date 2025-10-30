package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class ExportIT extends AbstractHelpBaseIT {

	// Feature: export

	// Scenario: basic export no classpath
	// When command('rm helloworld.jar')
	// When command('jbang export local helloworld.java')
	// Then match err contains "Exported to"
	// Then match err contains "helloworld.jar"
	@Test
	public void testBasicExportNoClasspath() {
		rmrf("helloworld.jar");
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
		rmrf("helloworld.jar", "lib");
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
		rmrf("classpath_example.jar", "lib");
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

	@Override
	protected String commandName() {
		return "export";
	}
}