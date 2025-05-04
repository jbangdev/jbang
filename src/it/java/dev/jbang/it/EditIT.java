package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class EditIT extends BaseIT {

	// Feature: edit

	// Scenario: edit no-open a file should print to std out
	// * command('jbang init hello.java')
	// * command('jbang edit -b --no-open hello.java')
	// * match err == '[jbang] Creating sandbox for script editing hello.java\n'
	// * match out contains 'hello'
	@Test
	public void testEditNoOpen() {
		shell("jbang init hello.java");
		assertThat(shell("jbang edit -b --no-open hello.java"))
			.succeeded()
			.errEquals(
					"[jbang] Creating sandbox for script editing hello.java"
							+ System.lineSeparator())
			.outContains("hello");
	}
}