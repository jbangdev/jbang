Feature: catalog testing

Scenario: java catalog list
  When command('jbang catalog add tc jbang-catalog.json')
  And command('jbang catalog list')
  Then match err contains "tc = JBang test scripts"

Scenario: add catalog and run catalog named reference
  When command('jbang catalog add tc jbang-catalog.json')
  Then command('jbang echo@tc tako')
  Then match out == "0:tako\n"

Scenario: add catalog and remove
  When command('jbang catalog add tc jbang-catalog.json')
  Then command('jbang catalog remove tc')
  Then command('jbang echo@tc tako')
  Then match err contains "Unknown catalog 'tc'"

Scenario: add catalog twice with same name
  When command('jbang catalog add tc jbang-catalog.json')
  Then command('jbang catalog add tc jbang-catalog.json')
  Then match exit == 2
  Then match err contains "A catalog with that name already exists"

Scenario: add catalog twice with different name
  When command('jbang catalog add tc jbang-catalog.json')
  Then command('jbang catalog add ct jbang-catalog.json')
  Then command('jbang echo@tc tako')
  And  match exit == 0
  Then command('jbang echo@ct tako')
  And match exit == 0
 