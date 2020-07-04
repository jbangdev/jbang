@java9orhigher
Feature: jshell

Background:
* if (javaversion == 8) karate.abort()
* if (windows) karate.abort()

Scenario: jsh system property
When command('jbang -Dvalue=hello hello.jsh')
Then match out == "hello\n"

Scenario: jsh quoted system property
When command('jbang -Dvalue="a quoted" hello.jsh')
Then match out == "a quoted\n"


Scenario: piping code via stdin
When command('echo \'System.out.println(\"Hello World\")\' | jbang -')
Then match out == "Hello World\n"
