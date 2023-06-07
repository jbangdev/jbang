Feature: integration

  Scenario: integration success
    * command('jbang integration/inttest.java')
    * match err !contains 'Integration... (out)'
    * match err contains 'Integration... (err)'
    * match out contains "Integration test"

  Scenario: integration success, verbose
    * command('jbang --verbose integration/inttest.java')
    * match err contains 'Integration... (out)'
    * match err contains 'Integration... (err)'
    * match out contains "Integration test"

  Scenario: integration failure
    * command('jbang -Dfailintegration=1 integration/inttest.java')
    * match err !contains 'Integration... (out)'
    * match err contains 'Integration... (err)'
    * match err contains 'Issue running postBuild()'
    * match err !contains 'Failing integration...'
    * match exit == 1

  Scenario: integration failure, verbose
    * command('jbang -Dfailintegration=1 --verbose integration/inttest.java')
    * match err contains 'Integration... (out)'
    * match err contains 'Integration... (err)'
    * match err contains 'Issue running postBuild()'
    * match err contains 'Failing integration...'
    * match exit == 1
