package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class IntegrationIT extends BaseIT {

	// Feature: integration

	// Scenario: integration success
	// * command('jbang integration/inttest.java')
	// * match err !contains 'Integration... (out)'
	// * match err contains 'Integration... (err)'
	// * match out contains "Integration test"
	//
	@Test
	public void testIntegrationSuccess() {
		assertThat(shell("jbang integration/inttest.java"))
			.succeeded()
			.errNotContains("Integration... (out)")
			.errContains("Integration... (err)")
			.outContains("Integration test");
	}

	// Scenario: integration success, verbose
	// * command('jbang --verbose integration/inttest.java')
	// * match err contains 'Integration... (out)'
	// * match err contains 'Integration... (err)'
	// * match out contains "Integration test"
	//
	@Test
	public void testIntegrationSuccessVerbose() {
		assertThat(shell("jbang --verbose integration/inttest.java"))
			.succeeded()
			.errContains("Integration... (out)")
			.errContains("Integration... (err)")
			.outContains("Integration test");
	}

	// Scenario: integration failure
	// * command('jbang -Dfailintegration=1 integration/inttest.java')
	// * match err !contains 'Integration... (out)'
	// * match err contains 'Integration... (err)'
	// * match err contains 'Issue running postBuild()'
	// * match err !contains 'Failing integration...'
	// * match exit == 1
	@Test
	public void testIntegrationFailure() {
		assertThat(shell("jbang -Dfailintegration=1 integration/inttest.java"))
			.exitedWith(1)
			.errNotContains("Integration... (out)")
			.errContains("Integration... (err)")
			.errContains("Issue running postBuild()")
			.errNotContains("Failing integration...");
	}

	//
	// Scenario: integration failure, verbose
	// * command('jbang -Dfailintegration=1 --verbose integration/inttest.java')
	// * match err contains 'Integration... (out)'
	// * match err contains 'Integration... (err)'
	// * match err contains 'Issue running postBuild()'
	// * match err contains 'Failing integration...'
	// * match exit == 1
	@Test
	public void testIntegrationFailureVerbose() {
		assertThat(shell("jbang -Dfailintegration=1 --verbose integration/inttest.java"))
			.exitedWith(1)
			.errContains("Integration... (out)")
			.errContains("Integration... (err)")
			.errContains("Issue running postBuild()")
			.errContains("Failing integration...");
	}
}