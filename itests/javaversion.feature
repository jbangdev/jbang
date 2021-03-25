Feature: java version control

  Scenario: java run non existent //java
    When command('jbang --verbose java4321.java')
    Then match err contains "incompatible, using Jbang managed version 4321"
    Then match err contains "Unable to download or install JDK version 4321"


  Scenario: java run with explicit java 8
    When command('jbang --verbose --java 8 java4321.java')
    Then match err !contains "incompatible, using Jbang managed version 4321"
    Then match err !contains "Unable to download or install JDK version 4321"