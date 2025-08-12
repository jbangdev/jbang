package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class WrapperIT extends AbstractHelpBaseIT {

	// Feature: wrapper

// # have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH%
// # to be cross platform.

// Scenario: test wrapper creation
// When command('jbang wrapper install --verbose -d ' + scratch)
//   * match exit == 0
//   * fileexist(scratch + "/jbang")
//   * fileexist(scratch + "/jbang.cmd")
//   * fileexist(scratch + "/.jbang/jbang.jar")
//   * command(scratch + '/jbang echo.java foo')
//   * match out == "0:foo\n"
	@Test
	public void shouldInstallWrapper() {
		assertThat(shell("jbang wrapper install --verbose -d " + scratch())).succeeded();
		assertThat(scratch().resolve("jbang")).isNotEmptyFile();
		assertThat(scratch().resolve("jbang.cmd")).isNotEmptyFile();
		assertThat(scratch().resolve(".jbang/jbang.jar")).isNotEmptyFile();
		assertThat(shell(scratch().resolve("jbang").toAbsolutePath() + " echo.java foo")).succeeded()
			.outIsExactly("0:foo"
					+ lineSeparator());
	}

// Scenario: test wrapper missing folder
// When command('jbang wrapper install -d foo')
//   * match exit == 2
//   * match err contains 'Destination folder does not exist'
	@Test
	public void shouldInstallWrapperMissingFolder() {
		assertThat(shell("jbang wrapper install -d foo")).exitedWith(2)
			.errContains("Destination folder does not exist");
	}

// Scenario: test wrapper exists
// When command('jbang wrapper install -d ' + scratch)
//   * match exit == 0
//   * command('jbang wrapper install -d ' + scratch)
//   * match exit == 0
//   * match err contains 'Wrapper already exists'
	@Test
	public void shouldInstallWrapperExists() {
		assertThat(shell("jbang wrapper install -d " + scratch())).succeeded();
		assertThat(shell("jbang wrapper install -d " + scratch())).exitedWith(0).errContains("Wrapper already exists");
	}

// Scenario: test wrapper force
// When command('jbang wrapper install -d ' + scratch)
//   * match exit == 0
//   * command('jbang wrapper install -f -d ' + scratch)
//   * match exit == 0
	@Test
	public void shouldInstallWrapperForce() {
		assertThat(shell("jbang wrapper install -d " + scratch())).succeeded();
		assertThat(shell("jbang wrapper install -f -d " + scratch())).succeeded();
	}

// Scenario: test plain wrapper install
// When command('jbang wrapper install')
//   * match exit == 0
	@Test
	public void shouldInstallWrapperPlain() {
		assertThat(shell("jbang wrapper install")).succeeded();
	}

	@Override
	protected String commandName() {
		return "wrapper";
	}
}