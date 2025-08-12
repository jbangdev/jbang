package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class AliasIT extends AbstractHelpBaseIT {

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

	@Override
	protected String commandName() {
		return "alias";
	}
}