package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

/**
 * Integration tests verifying CLI compatibility expectations. These cover
 * regressions identified in PR #2453: replace picocli with aesh
 *
 * These tests encode the expected behavior from the picocli-based CLI and
 * should be merged to main independently of the migration PR so that any
 * migration can be validated mechanically.
 * 
 * Note: these tests are not exhaustive and do not cover all possible CLI
 * behaviors. They are intended to be a starting point for verifying the
 * compatibility of possible new CLI implementation.
 */
public class CliCompatibilityIT extends BaseIT {

	// -------------------------------------------------------------------
	// deprecated flag scan must not fire on script arguments
	// -------------------------------------------------------------------

	@Test
	@Description("Deprecated flags after script arg must be passed through as script parameters")
	public void deprecatedFlagAfterScriptArgIsPassedThrough() {
		// echo.java prints its arguments; --init after the script is a script arg
		assertThat(shell("jbang run echo.java --init"))
			.succeeded()
			.outContains("--init");
	}

	@Test
	@Description("Deprecated flags after -- must be passed through as script parameters")
	public void deprecatedFlagAfterDoubleDashIsPassedThrough() {
		assertThat(shell("jbang run echo.java -- --edit"))
			.succeeded()
			.outContains("--edit");
	}

	@Test
	@Description("Deprecated flags in leading position should still error")
	public void deprecatedFlagInLeadingPositionErrors() {
		assertThat(shell("jbang --init test.java"))
			.failed()
			.errContains("deprecated and now removed");
	}

	// -------------------------------------------------------------------
	// Review item 2: --itr shorthand for --ignore-transitive-repositories
	// -------------------------------------------------------------------

	@Test
	@Description("--itr shorthand must be accepted as alias for --ignore-transitive-repositories")
	public void itrShorthandIsAccepted() {
		// --itr should be silently accepted; the script should still run
		assertThat(shell("jbang run --itr helloworld.java"))
			.succeeded()
			.outContains("Hello World");
	}

	// -------------------------------------------------------------------
	// Review item 5: --source-type with invalid value should give
	// user-friendly error, not a raw Java exception
	// -------------------------------------------------------------------

	@Test
	@Description("Invalid --source-type should produce a user-friendly error message")
	public void invalidSourceTypeGivesUserFriendlyError() {
		assertThat(shell("jbang run --source-type invalid helloworld.java"))
			.failed()
			.errNotContains("java.lang.IllegalArgumentException")
			.errContains("invalid");
	}

	@Test
	@Description("Invalid --source-type should exit with INVALID_INPUT (2), not INTERNAL_ERROR (4)")
	public void invalidSourceTypeExitsWithInvalidInput() {
		assertThat(shell("jbang run --source-type invalid helloworld.java"))
			.exitedWith(2);
	}

	// -------------------------------------------------------------------
	// Review item 7a: bare 'jbang' (no args) should show help and exit 0
	// -------------------------------------------------------------------

	@Test
	@Description("Bare 'jbang' with no arguments should exit 0")
	public void bareJbangExitsZero() {
		assertThat(shell("jbang"))
			.succeeded();
	}

	@Test
	@Description("Bare 'jbang' should print usage information")
	public void bareJbangShowsUsage() {
		assertThat(shell("jbang"))
			.errContains("Usage:");
	}

	// -------------------------------------------------------------------
	// Review item 7b: group commands without subcommand should show
	// available subcommands, not just a terse error
	// -------------------------------------------------------------------

	@Test
	@Description("'jbang alias' without subcommand should list available subcommands")
	public void aliasWithoutSubcommandShowsSubcommands() {
		assertThat(shell("jbang alias"))
			.errContains("add")
			.errContains("list")
			.errContains("remove");
	}

	@Test
	@Description("'jbang export' without subcommand should list available subcommands")
	public void exportWithoutSubcommandShowsSubcommands() {
		assertThat(shell("jbang export"))
			.errContains("portable")
			.errContains("local");
	}

	@Test
	@Description("'jbang jdk' without subcommand should list available subcommands")
	public void jdkWithoutSubcommandShowsSubcommands() {
		assertThat(shell("jbang jdk"))
			.errContains("install")
			.errContains("list")
			.errContains("uninstall");
	}

	@Test
	@Description("'jbang cache' without subcommand should list available subcommands")
	public void cacheWithoutSubcommandShowsSubcommands() {
		assertThat(shell("jbang cache"))
			.errContains("clear");
	}

	@Test
	@Description("'jbang catalog' without subcommand should list available subcommands")
	public void catalogWithoutSubcommandShowsSubcommands() {
		assertThat(shell("jbang catalog"))
			.errContains("add")
			.errContains("list")
			.errContains("remove");
	}

	// -------------------------------------------------------------------
	// Review item 10: --format validation on alias list / jdk list
	// -------------------------------------------------------------------

	@Test
	@Description("'jbang alias list --format xml' should fail with invalid format error")
	public void aliasListInvalidFormatFails() {
		assertThat(shell("jbang alias list --format xml"))
			.failed();
	}

	@Test
	@Description("'jbang alias list --format json' should succeed")
	public void aliasListJsonFormatSucceeds() {
		assertThat(shell("jbang alias list --format json"))
			.succeeded();
	}

	@Test
	@Description("'jbang alias list --format text' should succeed")
	public void aliasListTextFormatSucceeds() {
		assertThat(shell("jbang alias list --format text"))
			.succeeded();
	}
}
