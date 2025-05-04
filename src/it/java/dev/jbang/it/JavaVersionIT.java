package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class JavaVersionIT extends BaseIT {

	// Feature: java version control

	// Scenario: java run non existent //java
	// When command('jbang --verbose java4321.java')
	// Then match err contains "JDK version is not available for installation: 4321"
	@Test
	public void testNonExistentJavaVersion() {
		assertThat(shell("jbang --verbose java4321.java"))
			.errContains(
					"No suitable JDK was found for requested version: 4321");
	}

	// Scenario: java run with explicit java 8
	// When command('jbang --verbose --java 8 java4321.java')
	// Then match err !contains "JDK version is not available for installation:
	// 4321"
	@Test
	public void testExplicitJava8() {
		assertThat(shell("jbang --verbose --java 8 java4321.java"))
			.errNotContains(
					"JDK version is not available for installation: 4321");
	}
}