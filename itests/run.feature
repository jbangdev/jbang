Feature: run

Scenario: should fail on missing file
* command('jbang notthere.java')
* match out contains 'Could not read script argument notthere.java'
* match exit == 0 // TODO: actually should not be 0

Scenario: parameter passing
* command('jbang helloworld.java jbangtest')
* match out == "[jbang] Building jar...\nHello jbangtest\n"


