package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class IntegrationIT extends BaseIT {
	@Test
	void testIntegration() {
		assertThat(shell("jbang --fresh integrations/main.java"))
			.errContains("Integration called!")
			.errContains("//RUNTIME_OPTIONS -Dfoo=fubar")
			.errContains("//CDS")
			.errNotContains("//CDS null")
			.outContains("Hello World")
			.outContains("foo: fubar")
			.outContains("bar: aap");
	}

	@Test
	void testNoIntegration() {
		assertThat(shell("jbang --fresh --no-integrations integrations/main.java"))
			.outContains("Wrong main!!!");
	}

	// --fresh ensures a full build so integrations are actually executed, not
	// served from cache
	@Test
	void testInScriptIntegrationSuccess() {
		assertThat(shell("jbang --fresh integration/inttest.java"))
			.succeeded()
			.errNotContains("Integration... (out)")
			.errContains("Integration... (err)")
			.outContains("Integration test");
	}

	@Test
	void testInScriptIntegrationSuccessVerbose() {
		assertThat(shell("jbang --fresh --verbose integration/inttest.java"))
			.succeeded()
			.errContains("Integration... (out)")
			.errContains("Integration... (err)")
			.outContains("Integration test");
	}

	@Test
	void testInScriptIntegrationFailure() {
		assertThat(shell("jbang --fresh -Dfailintegration=1 integration/inttest.java"))
			.exitedWith(1)
			.errNotContains("Integration... (out)")
			.errContains("Integration... (err)")
			.errContains("Issue running postBuild()")
			.errNotContains("Failing integration...");
	}

	@Test
	void testInScriptIntegrationFailureVerbose() {
		assertThat(shell("jbang --fresh -Dfailintegration=1 --verbose integration/inttest.java"))
			.exitedWith(1)
			.errContains("Integration... (out)")
			.errContains("Integration... (err)")
			.errContains("Issue running postBuild()")
			.errContains("Failing integration...");
	}
}
