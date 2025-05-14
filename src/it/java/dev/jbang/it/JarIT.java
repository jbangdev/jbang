package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

import org.junit.jupiter.api.Test;

public class JarIT extends BaseIT {
	// Feature: launching jars

	// Scenario: java launch file
	// When command('jbang helloworld.jar')
	// Then match out == "Hello World\n"
	@Test
	void testJavaLaunchFile() {
		assertThat(shell("jbang hellojar.jar"))
			.outEquals("Hello World" + lineSeparator());
	}

	// #TODO: find GAV with static void main
	// Scenario: java launch GAV
	// When command('jbang --main
	// picocli.codegen.aot.graalvm.ReflectionConfigGenerator
	// info.picocli:picocli-codegen:4.6.3')
	// Then match err contains "Missing required parameter: '<classes>'"
	@Test
	void testJavaLaunchGAV() {
		assertThat(shell(
				"jbang --main picocli.codegen.aot.graalvm.ReflectionConfigGenerator info.picocli:picocli-codegen:4.6.3"))
			.errContains(
					"Missing required parameter: '<classes>'");
	}
}