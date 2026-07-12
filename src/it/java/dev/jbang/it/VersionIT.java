package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

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
		assertThat(shell("jbang version")).succeeded()
			.outMatches(Pattern.compile(
					"(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?" + lineSeparator()))
			.errEquals("");
	}

	// Scenario: verbose version
	// * command('jbang --verbose version')
	// * match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
	// * match err contains 'Repository'
	// * match exit == 0
	@Test
	public void shouldVerboseVersion() {
		assertThat(shell("jbang --verbose version")).succeeded()
			.outMatches(Pattern.compile(
					"(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?" + lineSeparator()))
			.errContains("Repository");
	}

	// Scenario: verbose version via config
	// * command('jbang config set verbose true')
	// * command('jbang version')
	// * match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
	// * match err contains 'Repository'
	// * match exit == 0
	@Test
	public void shouldVerboseVersionConfig() {
		assertThat(shell("jbang config set verbose true")).succeeded();
		assertThat(shell("jbang version")).succeeded()
			.outMatches(Pattern.compile(
					"(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?" + lineSeparator()))
			.errContains("Repository");
	}

	// Scenario: verbose config overridden by --no-verbose
	// * command('jbang config set verbose true')
	// * command('jbang --no-verbose version')
	// * match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
	// * match err !contains 'Repository'
	// * match exit == 0
	@Test
	public void shouldVerboseVersionConfigOverride() {
		assertThat(shell("jbang config set verbose true")).succeeded();
		assertThat(shell("jbang --no-verbose version")).succeeded()
			.outMatches(Pattern.compile(
					"(?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?" + lineSeparator()))
			.errNotContains("Repository");
	}

}