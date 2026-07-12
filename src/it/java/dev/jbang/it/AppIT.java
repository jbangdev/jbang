package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.qameta.allure.Description;

public class AppIT extends AbstractHelpBaseIT {

	// Scenario: check quotes are kept when wrapped with quotes
	// * command('jbang app install --force --name jbang-itest-app-quote echo.java')
	// When command('$JBANG_DIR/bin/jbang-itest-app-quote \'foo *\'')
	// Then match out == "0:foo *\n"
	@Test
	@DisabledOnOs(OS.WINDOWS)
	@Description("check quotes are kept when wrapped with quotes")
	public void shouldKeepQuotes() {
		assertThat(shell("jbang app install --force --name jbang-itest-app-quote echo.java")).succeeded();
		assertThat(shell("$JBANG_DIR/bin/jbang-itest-app-quote 'foo *'")).succeeded()
			.outIsExactly(
					"0:foo *" + lineSeparator());
	}

	// Scenario: check quotes are kept when wrapped with quotes
	// * command('jbang app install --force --name jbang-itest-app-quote echo.java')
	// When command('%JBANG_DIR%\\bin\\jbang-itest-app-quote.cmd "foo *"')
	// Then match out == "0:foo *\n"
	@Test
	@EnabledOnOs(OS.WINDOWS)
	public void shouldKeepQuotesWindows() {
		assertThat(shell("jbang app install --force --name jbang-itest-app-quote echo.java")).succeeded();
		assertThat(shell(
				"%JBANG_DIR%\\bin\\jbang-itest-app-quote.cmd \"foo *\"" + lineSeparator()))
			.succeeded()
			.outIsExactly("0:foo *"
					+ lineSeparator());
	}

	// Scenario: app install with -D should keep -D attached to property name
	// Regression test for https://github.com/jbangdev/jbang/issues/2564
	@Test
	@DisabledOnOs(OS.WINDOWS)
	@Description("app install with -D should produce -Dkey=value (no space) in wrapper script")
	public void shouldKeepSystemPropertyAttached() {
		assertThat(shell("jbang app install --force --name jbang-itest-app-sysprop"
				+ " -Djavafx.preview=true -Dcamel.jbang.version=4.21.0 printjvmargs.java"))
			.succeeded();
		assertThat(shell("$JBANG_DIR/bin/jbang-itest-app-sysprop"))
			.succeeded()
			.outContains("JVMARG:-Djavafx.preview=true")
			.outContains("JVMARG:-Dcamel.jbang.version=4.21.0");
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Description("app install with -D should produce -Dkey=value (no space) in wrapper scripts on Windows")
	public void shouldKeepSystemPropertyAttachedWindows() {
		assertThat(shell("jbang app install --force --name jbang-itest-app-sysprop"
				+ " -Djavafx.preview=true -Dcamel.jbang.version=4.21.0 printjvmargs.java"))
			.succeeded();
		assertThat(shell("%JBANG_DIR%\\bin\\jbang-itest-app-sysprop.cmd"))
			.succeeded()
			.outContains("JVMARG:-Djavafx.preview=true")
			.outContains("JVMARG:-Dcamel.jbang.version=4.21.0");
	}

	@Override
	protected String commandName() {
		return "app";
	}
}