Feature: launching jars

Scenario: java launch file
When command('jbang helloworld.jar')
Then match out == "Hello World\n"

#TODO: find GAV with static void main
Scenario: java launch GAV
  When command('jbang info.picocli:picocli-codegen:4.2.0/picocli.codegen.aot.graalvm.ReflectionConfigGenerator')
  Then match err contains "Missing required parameter: <classes>"