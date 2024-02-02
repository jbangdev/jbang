Feature: catalog testing

Scenario: java catalog list
  When command('jbang catalog add --global --name averylongcatalogname jbang-catalog.json')
  And command('jbang catalog list')
  Then match out contains "averylongcatalogname"
  Then match out contains "JBang test scripts"

Scenario: add catalog and run catalog named reference
  When command('jbang catalog add --global --name tc jbang-catalog.json')
  Then command('jbang echo@tc tako')
  Then match out == "0:tako\n"

Scenario: add catalog and remove
  When command('jbang catalog add --global --name tc jbang-catalog.json')
  Then command('jbang catalog remove tc')
  Then command('jbang echo@tc tako')
  Then match err contains "Unknown catalog 'tc'"

Scenario: add catalog twice with same name
  When command('jbang catalog add --global --name tc jbang-catalog.json')
  Then command('jbang catalog add --global --name tc jbang-catalog.json')
  Then match err contains "A catalog with name 'tc' already exists, use '--force' to add anyway"
  Then match exit == 2

Scenario: force add catalog twice with same name
  When command('jbang catalog add --global --name tc jbang-catalog.json')
  Then command('jbang catalog add --global --name tc --force jbang-catalog.json')
  Then match exit == 0

Scenario: add catalog twice with different name
  When command('jbang catalog add --global --name tc jbang-catalog.json')
  Then command('jbang catalog add --global --name ct jbang-catalog.json')
  Then command('jbang echo@tc tako')
  And  match exit == 0
  Then command('jbang echo@ct tako')
  And match exit == 0

Scenario: access remote catalog
  When command('jbang build hello@jbangdev')
  Then  match exit == 0

Scenario: list remote catalog aliases
  When command('jbang alias list jbangdev/jbang-catalog')
  Then  match exit == 0
  And match out contains "@jbangdev"
  And match out !contains "@jbangdev/jbang-catalog"

Scenario: list remote catalog templates
  When command('jbang template list jbangdev/jbang-catalog')
  Then  match exit == 0
  And match out contains "@jbangdev"
  And match out !contains "@jbangdev/jbang-catalog"
