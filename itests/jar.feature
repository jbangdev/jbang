Feature: launching jars

Scenario: java launch file
When command('jbang helloworld.jar')
Then match out == "Hello World\n"

#TODO: find GAV with static void main
Scenario: java launch GAV
  When command('jbang --main picocli.codegen.aot.graalvm.ReflectionConfigGenerator info.picocli:picocli-codegen:4.5.0')
  Then match err contains "Missing required parameter: '<classes>'"