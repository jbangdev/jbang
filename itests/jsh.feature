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
