Feature: edit

Scenario: edit no-open a file should print to std out
* command('jbang init hello.java')
  * command('jbang edit --no-open hello.java')
* match err == ''
  * match out contains 'hello'
