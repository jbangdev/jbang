Feature: init

Scenario: init of file and run
When command('jbang init $SCRATCH/test.java') 
* match exit == 0
* def contents = read(scratch + '/test.java')
* match contents contains "class test"
* command('jbang $SCRATCH/test.java')
* match err == "[jbang] Building jar...\n"
* command('jbang $SCRATCH/test.java')
* match err !contains "[jbang] Building jar"



