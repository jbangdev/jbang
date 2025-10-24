package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
public class RunNixIT extends BaseIT {

// 	Feature: run on non-windows

// Background:
//   * if (windows) karate.abort()

// Scenario: as code option 2
//   * command('jbang --code "$(cat helloworld.java)" jbangtest')
//   * match err == "[jbang] Building jar for helloworld.java...\n"
//   * match out == "Hello jbangtest\n"
	@Test
	public void shouldRunAsCodeOption2() {
		assertThat(shell("jbang --code \"$(cat helloworld.java)\" jbangtest"))
			.errContains(
					"[jbang] Building jar for helloworld.java...\n")
			.outIsExactly("Hello jbangtest\n");
	}

// Scenario: as code option 3
//   * command('jbang "--code=$(cat helloworld.java)" jbangtest')
//   * match err == "[jbang] Building jar for helloworld.java...\n"
//   * match out == "Hello jbangtest\n"
	@Test
	public void shouldRunAsCodeOption3() {
		// TODO: fresh should not be needed. isolation issue.
		assertThat(shell("jbang --fresh \"--code=$(cat helloworld.java)\" jbangtest"))
			.errIsExactly(
					"[jbang] Building jar for helloworld.java...\n")
			.outIsExactly(
					"Hello jbangtest\n");
	}

	@Test
	public void shouldNotLeaveVariables() {
		assertThat(shell("jbang run -c \"System.exit(0)\" ; echo $JBANG_RUNTIME_SHELL"))
			.outMatches(Pattern.compile("\\s*"));
	}

}
