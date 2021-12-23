Feature: cache

Scenario: clear cache default
When command('jbang cache clear')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"

Scenario: clear cache default
When command('jbang cache clear --all')
* match exit == 0
* match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for jdks\n[jbang] Clearing cache for kotlincs\n[jbang] Clearing cache for groovycs\n[jbang] Clearing cache for projects\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"

Scenario: clear cache default
When command('jbang cache clear --all --no-jdk --no-url --no-jar --no-project --no-script --no-stdin --no-deps --no-kotlinc')
* match exit == 0
* match err == ""

