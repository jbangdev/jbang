Feature: export

Scenario: basic export no classpath
  When command('rm helloworld.jar')
  When command('jbang export local helloworld.java')
  Then match err contains "Exported to"
  Then match err contains "helloworld.jar"

  Scenario: basic export slim no classpath
    When command('rm -rf helloworld.jar lib')
    When command('jbang export portable helloworld.java')
    Then match err contains "Exported to"
    Then match err contains "helloworld.jar"



