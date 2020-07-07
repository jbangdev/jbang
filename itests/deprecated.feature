Feature: deprecated and removed features

Scenario: --init should error and tell user about alternative
When command('jbang --init ' + scratch + '/test.java')
* match exit == 2
* match err contains "deprecated and now removed"

Scenario: --trust should error and tell user about alternative
When command('jbang --trust test.java')
* match exit == 2
* match err contains "deprecated and now removed"

Scenario: --trust should error and tell user about alternative
When command('jbang --edit-live=idea test.java')
* match exit == 2
* match err contains "deprecated and now removed"


