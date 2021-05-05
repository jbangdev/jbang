Feature: quoting for windows

Background:
* if (!windows) karate.abort()

Scenario: check quotes are kept when wrapped with quotes
* command('jbang app install --force --name jbang-itest-app-quote echo.java')
When command('%JBANG_DIR%\\bin\\jbang-itest-app-quote.cmd "foo *"')
Then match out == "0:foo *\n"
