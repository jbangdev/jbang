package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class RunKotlinIT extends BaseIT {

// 	Feature: run-kotlin

// Scenario: should not fail to run kotlin
// * command('jbang runkotlin.kt')
// * match out contains 'SUCCESS!'
// * match exit == 0
	@Test
	public void shouldRunKotlin() {
		assertThat(shell("jbang runkotlin.kt")).outContains("SUCCESS!").exitedWith(0);
	}

}