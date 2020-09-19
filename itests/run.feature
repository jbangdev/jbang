Feature: run

Scenario: should fail on missing file
* command('jbang notthere.java')
* match err contains 'Could not read script argument notthere.java'
* match exit == 2

Scenario: parameter passing
* command('jbang helloworld.java jbangtest')
* match err == "[jbang] Building jar...\n"
* match out == "Hello jbangtest\n"

Scenario: java launch helloworld with jfr
  When command('jbang --jfr helloworld.java')
  Then match out contains "Started recording 1. No limit specified, using maxsize=250MB as default."


Scenario: java run multiple sources
  When command('jbang --verbose one.java')
  Then match out contains "Two"

