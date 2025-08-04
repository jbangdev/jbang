package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class ConfigIT extends AbstractHelpBaseIT {

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

	@Override
	protected String commandName() {
		return "config";
	}
}