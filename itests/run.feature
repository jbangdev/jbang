Feature: run

Scenario: should fail on missing file
* command('jbang notthere.java')
* match err contains 'Script or alias could not be found or read: \'notthere.java\''
* match exit == 2

Scenario: parameter passing
* command('jbang helloworld.java jbangtest')
* match err == "[jbang] Building jar...\n"
* match out == "Hello jbangtest\n"

Scenario: std in
* command('cat helloworld.java | jbang - jbangtest')
* match err == "[jbang] Building jar...\n"
* match out == "Hello jbangtest\n"

Scenario: java launch helloworld with jfr
  When command('jbang --jfr helloworld.java')
  Then match out contains "Started recording 1. No limit specified, using maxsize=250MB as default."

Scenario: java run multiple sources
  When command('jbang --verbose one.java')
  Then match out contains "Two"

Scenario: java run multiple matching sources
  When command('jbang RootOne.java')
  Then match out contains "NestedOne"
  Then match out contains "NestedTwo"

Scenario: java run multiple sources via cli
  When command('jbang -s bar/Bar.java foo.java')
  Then match out contains "Bar"

Scenario: java run multiple files
  When command('jbang res/resource.java')
  Then match out contains "hello properties"

Scenario: java run multiple files using alias
  When command('jbang resource')
  Then match out contains "hello properties"

Scenario: java run multiple files using remote alias
  When command('jbang catalog add --name test https://raw.githubusercontent.com/jbangdev/jbang/HEAD/itests/jbang-catalog.json')
  Then command('jbang trust add https://raw.githubusercontent.com')
  Then command('jbang resource@test')
  Then match out contains "hello properties"

Scenario: java run with agent
  When command('jbang --verbose --javaagent=JULAgent.java=options JULTest.java World')
  Then match err contains "info World"
  Then match err contains "options"
