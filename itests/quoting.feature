@java9orhigher
Feature: jshell

Background:
* if (javaversion == 8) karate.abort()
* if (windows) karate.abort()

Scenario: piping code via stdin
When command('echo \'System.out.println(\"Hello World\")\' | jbang -')
Then match out == "Hello World\n"

Scenario: check quotes are kept when wrapped with quotes
When command('jbang echo.java \'foo *\'')
Then match out == "0:foo *\n"

Scenario: check expansion does happen
When command('jbang echo.java foo *')
Then match out contains "0:foo\n1:"
