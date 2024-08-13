Feature: run-kotlin

Scenario: should not fail to run kotlin
* command('jbang runkotlin.kt')
* match out contains 'SUCCESS!'
* match exit == 0
