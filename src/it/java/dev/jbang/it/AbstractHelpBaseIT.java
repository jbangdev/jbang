package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

public abstract class AbstractHelpBaseIT extends BaseIT {

	protected abstract String commandName();

	// Scenario: check help command is printed when -h is requested
	// * command('jbang <command> --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang " + commandName() + " --help"))
			.succeeded()
			.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang <command> --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang " + commandName()))
			.failed()
			.errContains("Missing required subcommand");
	}
}
