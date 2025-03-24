package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
public class AppIT extends BaseIT {


    // Scenario: check quotes are kept when wrapped with quotes
    // * command('jbang app install --force --name jbang-itest-app-quote echo.java')
    // When command('$JBANG_DIR/bin/jbang-itest-app-quote \'foo *\'')
    // Then match out == "0:foo *\n"
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void shouldKeepQuotes() {
        assertThat(shell("jbang app install --force --name jbang-itest-app-quote echo.java")).succeeded();
        assertThat(shell("$JBANG_DIR/bin/jbang-itest-app-quote 'foo *'")).succeeded().outIsExactly("0:foo *\n");
    }

    //     Scenario: check quotes are kept when wrapped with quotes
    // * command('jbang app install --force --name jbang-itest-app-quote echo.java')
    // When command('%JBANG_DIR%\\bin\\jbang-itest-app-quote.cmd "foo *"')
    // Then match out == "0:foo *\n"
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void shouldKeepQuotesWindows() {
        assertThat(shell("jbang app install --force --name jbang-itest-app-quote echo.java")).succeeded();
        assertThat(shell("%JBANG_DIR%\\bin\\jbang-itest-app-quote.cmd 'foo *'")).succeeded().outIsExactly("0:foo *\n");
    }


}