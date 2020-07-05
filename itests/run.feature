Feature: run

Scenario: should fail on missing file
* command('jbang notthere.java')
* match err contains 'Could not read script argument notthere.java'
* match exit == 1

Scenario: parameter passing
* command('jbang helloworld.java jbangtest')
* match err == "[jbang] Building jar...\n"
* match out == "Hello jbangtest\n"


