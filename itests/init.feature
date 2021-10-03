Feature: init

Scenario: init of file and run
# have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH% 
# to be cross platform.
When command('jbang init ' + scratch + '/test.java') 
* match exit == 0
* def contents = read(scratch + '/test.java')
* match contents contains "class test"
* command('jbang ' + scratch + '/test.java')
* match err == "[jbang] Building jar...\n"
* command('jbang ' + scratch + '/test.java')
* match err !contains "[jbang] Building jar"

  Scenario: init of file in a non existing directory
# have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH%
# to be cross platform.
When command('jbang init ' + scratch + '/newfolder/test.java')
  * match exit == 0
  * def contents = read(scratch + '/newfolder/test.java')
  * match contents contains "class test"
  * command('jbang ' + scratch + '/newfolder/test.java')
  * match err == "[jbang] Building jar...\n"
  * command('jbang ' + scratch + '/newfolder/test.java')
  * match err !contains "[jbang] Building jar"

Scenario: init with several file-refs template should display a message regarding the first file using {basename}
# have to manually use 'scratch' as can't use $SCRATCH nor %SCRATCH%
# to be cross platform.
When command('jbang template add --name test "{basename}Test.java=templates/test.java.qute" "{basename}SecondTest.java=templates/test.java.qute"')
Then command('jbang --verbose init -t test ' + scratch + '/Script.java')
#
* match exit == 0
* fileexist(scratch + '/ScriptTest.java')
* fileexist(scratch + '/ScriptSecondTest.java')
* match err contains "File initialized. You can now run it with"
* match err contains "or edit it using"
* match err contains "ScriptTest.java"
# * match err !contains "ScriptSecondTest.java"
