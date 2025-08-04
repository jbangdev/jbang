package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;

public class TemplateIT extends BaseIT {

	// Scenario: Removing built-in template
	// When command('jbang template remove hello')
	// * match exit == 0
	// * match err contains "Cannot remove template hello from built-in catalog"
	@Test
	public void shouldRemoveBuiltInTemplate() {
		assertThat(shell("jbang template remove hello")).succeeded()
			.errContains(
					"Cannot remove template hello from built-in catalog");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang template --help"))
				.succeeded()
				.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang template"))
				.failed()
				.errContains("Missing required subcommand");
	}

}