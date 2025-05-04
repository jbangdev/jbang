package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

//TODO: fresh should not be needed. probably due to not running isolated enough.

public class MarkdownIT extends BaseIT {

	// Feature: markdown

	// Scenario: readme.md
	// * command('jbang readme.md')
	// * match err contains "[jbang] Resolving dependencies..."
	// * match out contains "You have no arguments!"
	@Test
	public void testReadmeMd() {
		assertThat(shell("jbang --fresh readme.md"))
			.succeeded()
			.errContains("[jbang] Resolving dependencies...")
			.outContains("You have no arguments!");
	}

	// Scenario: readme.md with args
	// * command('jbang readme.md wonderful world')
	// * match err contains "[jbang] Resolving dependencies..."
	// * match out contains "You have 2 arguments! First is wonderful\n"
	@Test
	public void testReadmeMdWithArgs() {

		assertThat(shell("jbang --fresh readme.md wonderful world"))
			.succeeded()
			.errContains("[jbang] Resolving dependencies...")
			.outContains(
					"You have 2 arguments! First is wonderful");
	}
}