Feature: edit

Scenario: edit a file should print to std out
* command('jbang init hello.java')
  * command('jbang edit hello.java')
* match err == ''
  * match out contains 'hello'
