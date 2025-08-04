package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;

public class ConfigIT extends BaseIT {

	// Feature: config

	// Scenario: Configuration keys can be updated
	// When command('jbang config set foo bar')
	// * match exit == 0
	// When command('jbang config set foo baz')
	// * match exit == 0
	// Then command('jbang config list')
	// * match exit == 0
	// * match out contains "foo = baz"
	@Test
	public void testConfigKeyUpdate() {
		assertThat(shell("jbang config set foo bar"))
			.succeeded();

		assertThat(shell("jbang config set foo baz"))
			.succeeded();

		assertThat(shell("jbang config list"))
			.succeeded()
			.outContains("foo = baz");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang config --help"))
				.succeeded()
				.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang config"))
				.failed()
				.errContains("Missing required subcommand");
	}
}