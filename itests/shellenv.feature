Feature: env variables

Scenario: JBANG_RUNTIME_SHELL available
When command('jbang env@jbangdev jbang')
Then match err contains "^JBANG_RUNTIME_SHELL"

Scenario: JBANG_STDIN_NOTTY available
  When command('jbang env@jbangdev jbang')
  Then match err contains "^JBANG_STDIN_NOTTY"

Scenario: JBANG_LAUNCH_CMD available
  When command('jbang env@jbangdev jbang')
  Then match err contains "^JBANG_LAUNCH_CMD"
