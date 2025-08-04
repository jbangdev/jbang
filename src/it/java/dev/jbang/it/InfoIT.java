package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;

public class InfoIT extends BaseIT {

	@Test
	public void shouldPrintNiceDocs() {
		assertThat(shell("jbang info docs docsexample.java"))
			.outContains("This is a description")
			.errIsExactly("[jbang] Use --open to open the documentation file in the default browser." + lineSeparator())
			.outContains("main:")
			.outContains("  https://xam.dk/notthere")
			.outContains("  does-not-exist.txt (not found)")
			.outContains("javadoc:")
			.outContains("  /tmp/this_exists.txt")
			.succeeded();
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang info --help"))
				.succeeded()
				.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang info"))
				.failed()
				.errContains("Missing required subcommand");
	}
}