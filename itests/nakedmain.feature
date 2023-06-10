Feature: jep445

Background:
// as long as java 21 is not downloadable
// we keep this abort in place - it should be removed
// until then test by:
// JBANG_JDK_VENDOR=openjdk ./karate.java jep445.feature
* if (java.lang.System.getenv('JBANG_JDK_VENDOR') != "openjdk") karate.abort()

Scenario: naked main
When command('jbang nakedmain/noclass.java')
Then match out == "Hello!\n"

Scenario: naked main
When command('jbang nakedmain/noclasswithargs.java hey')
Then match out == "Hello hey\n"

Scenario: naked main
When command('jbang nakedmain/classinstance.java yo')
Then match out == "Hello yo\n"
