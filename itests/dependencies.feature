Feature: dependency fetching

Scenario: fetch dependencies
* command('jbang --verbose version')
When command('jbang classpath_log.java', { JBANG_REPO: scratch + "/newrepo"})
Then match err == '[jbang] Resolving dependencies...\n[jbang]     Resolving log4j:log4j:1.2.17...Done\n[jbang] Dependencies resolved\n[jbang] Building jar...\n'
And fileexist(scratch + "/newrepo")
And match exit == 0
