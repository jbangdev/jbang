package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class VersionIT extends BaseIT {

	// Feature: version command

	// Scenario: version
	// * command('jbang version')
	// * match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
	// * match err == ''
	// * match exit == 0
	@Test
	public void shouldVersion() {
		assertThat(shell("jbang version")).succeeded().outMatches(Pattern.compile("(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?\n")).errEquals("");
	}

	// Scenario: verbose version
	// * command('jbang --verbose version')
	// * match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
	// * match err contains 'Repository'
	// * match exit == 0
	@Test
	public void shouldVerboseVersion() {
		assertThat(shell("jbang --verbose version")).succeeded().outMatches(Pattern.compile("(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?\n")).errContains("Repository");
	}

}