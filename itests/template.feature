Feature: template

Scenario: Removing built-in template
When command('jbang template remove hello')
* match exit == 0
* match err contains "Cannot remove template hello from built-in catalog"
