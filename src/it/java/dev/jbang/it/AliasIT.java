package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;

public class AliasIT extends BaseIT {

	// Scenario: No properties should be displayed for an alias having none
	// When command('jbang alias add -f ' + scratch + ' echo.java')
	// * match exit == 0
	// Then command('jbang alias list -f ' + scratch)
	// * match exit == 0
	// * match out !contains "Properties"
	// Then command('jbang alias remove -f ' + scratch + ' echo')
	// * match exit == 0
	@Test
	public void shouldAddAlias() {
		assertThat(shell("jbang alias add -f " + scratch() + " echo.java")).succeeded();
		assertThat(shell("jbang alias list -f " + scratch())).succeeded().outNotContains("Properties");
		assertThat(shell("jbang alias remove -f " + scratch() + " echo")).succeeded();
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check --help write help on console for top level command")
	public void shouldPrintHelp() {
		assertThat(shell("jbang alias --help"))
				.succeeded()
				.errContains("Use 'jbang <command> -h' for detailed");
	}

	// Scenario: check help command is printed when -h is requested
	// * command('jbang app --help')
	@Test
	@Description("Check prints detailed help on missed subcommand argument")
	public void shouldPrintDetailedHelpOnMissedSubcommand() {
		assertThat(shell("jbang alias"))
				.failed()
				.errContains("Missing required subcommand");
	}

}