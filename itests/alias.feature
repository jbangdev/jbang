Feature: alias

Scenario: No properties should be displayed for an alias having none
When command('jbang alias add -f ' + scratch + ' echo.java')
* match exit == 0
Then command('jbang alias list -f ' + scratch)
* match exit == 0
* match out !contains "Properties"
Then command('jbang alias remove -f ' + scratch + ' echo')
* match exit == 0
