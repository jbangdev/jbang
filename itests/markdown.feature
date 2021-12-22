@java9orhigher
Feature: markdown

Background:
* if (javaversion == 8) karate.abort()

Scenario: readme.md
* command('jbang readme.md')
* match err contains "[jbang] Resolving dependencies..."
* match out contains "You have no arguments!"

Scenario: readme.md with args
* command('jbang readme.md wonderful world')
* match err contains "[jbang] Resolving dependencies..."
* match out contains "You have 2 arguments! First is wonderful\n"
