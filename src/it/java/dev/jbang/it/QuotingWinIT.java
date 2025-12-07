package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
public class QuotingWinIT extends BaseIT {

	// Scenario: check quotes are kept when wrapped with quotes
	// When command('jbang echo.java "foo *"')
	// Then match out == "0:foo *\n"
	@Test
	public void shouldKeepQuotesWhenWrappedWithQuotes() {
		assertThat(shell("jbang echo.java \"foo *\"")).outIsExactly("0:foo *" + lineSeparator());
	}

	// Scenario: check expansion does happen
	// When command('jbang echo.java foo *')
	// Then match out contains "0:foo\n1:"
	@Test
	public void shouldExpand() {
		assertThat(shell("jbang echo.java foo *")).outContains("0:foo" + lineSeparator() + "1:");
	}

	// Scenario: check special characters on command line work
	// When command('jbang echo.java " ~!@#$%^&*()-+\\:;\'`<>?/,.{}[]""')
	// Then match out == "0: ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"\n"
	@Test
	public void shouldHandleSpecialCharacters() {
		assertThat(shell("jbang echo.java \" ~!@#$%^&*()-+\\:;\'`<>?/,.{}[]\"")).outIsExactly(
				"0: ~!@#$%^&*()-+\\:;'`<>?/,.{}[]" + lineSeparator());
	}

	// Scenario: check spaces in JBANG_DIR path work (Issue #171)
	// When command('jbang echo.java "foo *"', { JBANG_DIR: scratch + '\\jbang dir
	// test' })
	// Then match out == "0:foo *\n"
	@Test
	public void shouldHandleSpacesInJBANG_DIRPath() {
		assertThat(shell(
				Collections.singletonMap("JBANG_DIR", scratch().resolve("jbang dir test").toString()),
				"jbang echo.java \"foo *\""))
			.outIsExactly("0:foo *" + lineSeparator());
	}
}