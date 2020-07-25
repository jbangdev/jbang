Feature: cache

Scenario: clear cache default
When command('jbang cache clear')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n"

Scenario: clear cache default
When command('jbang cache clear --all')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for jdks\n"

Scenario: clear cache default
When command('jbang cache clear --all --no-jdk --no-url --no-jar')
* match exit == 0
* match err == ""

