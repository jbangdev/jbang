Feature: run on non-windows

Background:
  * if (windows) karate.abort()

Scenario: as code option 2
  * command('jbang --code "$(cat helloworld.java)" jbangtest')
  * match err == "[jbang] Building jar for helloworld.java...\n"
  * match out == "Hello jbangtest\n"

Scenario: as code option 3
  * command('jbang "--code=$(cat helloworld.java)" jbangtest')
  * match err == "[jbang] Building jar for helloworld.java...\n"
  * match out == "Hello jbangtest\n"
