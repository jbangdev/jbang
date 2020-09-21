Feature: version command

Scenario: version
* command('jbang version')
* match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
* match err == ''
* match exit == 0

Scenario: verbose version
* command('jbang --verbose version')
* match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
* match err contains 'Repository'
* match exit == 0

