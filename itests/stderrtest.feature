Feature: stderrtest - requires Java 10 or higher to run

Background:
* if (javaversion < 10) karate.abort()

Scenario: java helloerr
When command('java hello.java')
Then match out == "I am on stdout\n"
Then match err == "I am on stderr\n"


