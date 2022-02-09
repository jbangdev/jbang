Feature: wrapper

# have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH%
# to be cross platform.

Scenario: test wrapper creation
When command('jbang wrapper install --verbose -d ' + scratch)
  * match exit == 0
  * fileexist(scratch + "/jbang")
  * fileexist(scratch + "/jbang.cmd")
  * fileexist(scratch + "/.jbang/jbang.jar")
  * command(scratch + '/jbang echo.java foo')
  * match out == "0:foo\n"

Scenario: test wrapper missing folder
When command('jbang wrapper install -d foo')
  * match exit == 2
  * match err contains 'Destination folder does not exist'

Scenario: test wrapper exists
When command('jbang wrapper install -d ' + scratch)
  * match exit == 0
  * command('jbang wrapper install -d ' + scratch)
  * match exit == 0
  * match err contains 'Wrapper already exists'

Scenario: test wrapper force
When command('jbang wrapper install -d ' + scratch)
  * match exit == 0
  * command('jbang wrapper install -f -d ' + scratch)
  * match exit == 0

Scenario: test plain wrapper install
When command('jbang wrapper install')
  * match exit == 0

