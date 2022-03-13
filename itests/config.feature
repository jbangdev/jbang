Feature: config

Scenario: Configuration keys can be updated
When command('jbang config set run.debug 7272')
* match exit == 0
When command('jbang config set init.template cli')
* match exit == 0
Then command('jbang config set run.debug 7373')
* match exit == 0
Then command('jbang config get run.debug')
* match exit == 0
* match out contains "7373"
Then command('jbang config list')
* match exit == 0
* match out contains "run.debug"
* match out contains "= 7373"
