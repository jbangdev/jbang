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

	@Override
	protected String commandName() {
		return "app";
	}
}