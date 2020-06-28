Feature: version command

Scenario: version
* command('jbang version')
* match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
* match exit == 0

Scenario: verbose version
* command('jbang --verbose version')
* match out contains 'Repository'
* match exit == 0

