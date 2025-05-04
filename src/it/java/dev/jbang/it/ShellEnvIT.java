package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class ShellEnvIT extends BaseIT {

	// Feature: env variables

	// Scenario: JBANG_RUNTIME_SHELL available
	// When command('jbang env@jbangdev JBANG')
	// Then match out contains "JBANG_RUNTIME_SHELL"
	@Test
	public void testRuntimeShellAvailable() {
		assertThat(shell("jbang env@jbangdev JBANG"))
			.succeeded()
			.outContains("JBANG_RUNTIME_SHELL");
	}

	// Scenario: JBANG_STDIN_NOTTY available
	// When command('jbang env@jbangdev JBANG')
	// Then match out contains "JBANG_STDIN_NOTTY"
	@Test
	public void testStdinNottyAvailable() {
		assertThat(shell("jbang env@jbangdev JBANG"))
			.succeeded()
			.outContains("JBANG_STDIN_NOTTY");
	}

	// Scenario: JBANG_LAUNCH_CMD available
	// When command('jbang env@jbangdev JBANG')
	// Then match out contains "JBANG_LAUNCH_CMD"
	@Test
	public void testLaunchCmdAvailable() {
		assertThat(shell("jbang env@jbangdev JBANG"))
			.succeeded()
			.outContains("JBANG_LAUNCH_CMD");
	}
}