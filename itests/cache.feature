Feature: cache

Scenario: clear cache default
When command('jbang cache clear')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n"

Scenario: clear cache default
When command('jbang cache clear --all')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for jbangs\n[jbang] Clearing cache for jdks\n[jbang] Clearing cache for projects\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n"

Scenario: clear cache default
When command('jbang cache clear --all --no-jdk --no-url --no-jar --no-project --no-script --no-stdin')
* match exit == 0
* match err == ""

