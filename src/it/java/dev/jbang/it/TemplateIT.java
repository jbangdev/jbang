package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class TemplateIT extends AbstractHelpBaseIT {

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

	@Override
	protected String commandName() {
		return "template";
	}
}