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



