Feature: config

Scenario: Configuration keys can be updated
When command('jbang config set foo bar')
* match exit == 0
When command('jbang config set foo baz')
* match exit == 0
Then command('jbang config list')
* match exit == 0
* match out contains "foo = baz"
