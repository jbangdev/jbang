@java9orhigher
Feature: jshell

Background:
* if (javaversion == 8) karate.abort()

Scenario: jshell helloworld
When command('jbang helloworld.jsh')
Then match out == "Hello World\n"

Scenario: jshell arguments
When command('jbang helloworld.jsh JSH!')
Then match out == "Hello JSH!\n"

Scenario: jsh default system property
  When command('jbang -Dvalue hello.jsh')
  Then match out == "true\n"

  Scenario: jsh system property
When command('jbang -Dvalue=hello hello.jsh')
Then match out == "hello\n"

Scenario: jsh quoted system property
When command('jbang -Dvalue="a quoted" hello.jsh')
Then match out == "a quoted\n"

Scenario: jsh fail on --native
  When command('jbang --native hello.jsh')
  Then match err contains ".jsh cannot be used with --native"

Scenario: force jsh
  When command('jbang --jsh hellojsh hello')
  Then match err == ""
  Then match out == "hello\n"
