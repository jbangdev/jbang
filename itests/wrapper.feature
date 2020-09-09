Feature: wrapper

Scenario: test wrapper creation
# have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH% 
# to be cross platform.
When command('jbang wrapper --verbose -d ' + scratch)
  * match exit == 0
  * fileexist(scratch + "/jbang")
  * fileexist(scratch + "/jbang.cmd")
  * fileexist(scratch + "/.jbang/jbang.jar")
  * command(scratch + '/jbang echo.java \'foo\'')
  * match out == "0:foo\n"
