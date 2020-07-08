Feature: launching jars

Scenario: java launch file
When command('jbang helloworld.jar')
Then match out == "Hello World\n"

#TODO: find GAV with static void main
# Scenario: java launch GAV
#  When command('jbang com.intuit.karate:karate-netty:jar:0.9.5')
#  Then match out == "Hello World\n"