Feature: launching jars

Scenario: java launch file
When command('jbang helloworld.jar')
Then match out == "Hello World\n"


Scenario: java launch GAV
  When command('jbang com.intuit.karate:karate-apache:jar:0.9.5')
  Then match out == "Hello World\n"