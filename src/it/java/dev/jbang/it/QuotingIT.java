package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
public class QuotingIT extends BaseIT {

// Scenario: piping code via stdin
// * if (javaversion == 8) karate.abort()
// When command('echo \'System.out.println(\"Hello World\")\' | jbang -')
// Then match out == "Hello World\n"
	@Test
	@DisabledOnJre(JRE.JAVA_8)
	public void shouldPipeCodeViaStdin() {
		assertThat(shell("echo 'System.out.println(\"Hello World\")' | jbang -")).outIsExactly("Hello World\n");
	}

// Scenario: check quotes are kept when wrapped with quotes
// When command('jbang echo.java \'foo *\'')
// Then match out == "0:foo *\n"
	@Test
	@DisabledOnJre(JRE.JAVA_8)
	public void shouldKeepQuotesWhenWrappedWithQuotes() {
		assertThat(shell("jbang echo.java 'foo *'")).outIsExactly("0:foo *\n");
	}

// Scenario: check expansion does happen
// When command('jbang echo.java foo *')
// Then match out contains "0:foo\n1:"
	@Test
	@DisabledOnJre(JRE.JAVA_8)
	public void shouldExpand() {
		assertThat(shell("jbang echo.java foo *")).outContains("0:foo\n1:");
	}

// Scenario: check special characters on command line work
// When command('jbang echo.java \' ~!@#$%^&*()-+\\:;\'\\\'\'`<>?/,.{}[]"\'')
// Then match out == "0: ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"\n"
	@Test
	public void shouldHandleSpecialCharacters() {
		assertThat(shell("jbang echo.java ' ~!@#$%^&*()-+\\:;\'\\\'\'`<>?/,.{}[]\"'")).outIsExactly(
				"0: ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"\n");
	}

// Scenario: check spaces in JBANG_DIR path work (Issue #171)
// When command('jbang echo.java \'foo *\'', { JBANG_DIR: scratch + '/jbang dir test' })
// Then match out == "0:foo *\n"
	@Test
	public void shouldHandleSpacesInJBANG_DIRPath() {
		assertThat(shell(
				Collections.singletonMap("JBANG_DIR", scratch().resolve("jbang dir test").toString()),
				"jbang echo.java 'foo *'"))
			.outIsExactly("0:foo *\n");
	}
}