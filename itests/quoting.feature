Feature: quoting

Background:
* if (windows) karate.abort()

Scenario: piping code via stdin
* if (javaversion == 8) karate.abort()
When command('echo \'System.out.println(\"Hello World\")\' | jbang -')
Then match out == "Hello World\n"

Scenario: check quotes are kept when wrapped with quotes
When command('jbang echo.java \'foo *\'')
Then match out == "0:foo *\n"

Scenario: check expansion does happen
When command('jbang echo.java foo *')
Then match out contains "0:foo\n1:"

Scenario: check special characters on command line work
When command('jbang echo.java \' ~!@#$%^&*()-+\\:;\'\\\'\'`<>?/,.{}[]"\'')
Then match out == "0: ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"\n"

Scenario: check spaces in JBANG_DIR path work (Issue #171)
When command('jbang echo.java \'foo *\'', { JBANG_DIR: scratch + '/jbang dir test' })
Then match out == "0:foo *\n"
