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
}
