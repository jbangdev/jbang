Feature: output

Scenario: test for clean output
When command('jbang alias remove dummy-alias-name')
* match exit == 0
* match out == ""
* match err == ""
