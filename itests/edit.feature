Feature: edit

Scenario: edit no-open a file should print to std out
* command('jbang init hello.java')
  * command('jbang edit -b --no-open hello.java')
* match err == '[jbang] Creating sandbox for script editing hello.java\n'
  * match out contains 'hello'
