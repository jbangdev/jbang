#!/bin/bash

export GRADLE_VERSION=8.9

mv gradle/wrapper/gradle-wrapper-jar gradle/wrapper/gradle-wrapper.jar

touch build.gradle
touch settings.gradle

./gradlew wrapper --gradle-version $GRADLE_VERSION

mv gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper-jar

rm -f -r .gradle build.gradle settings.gradle

tree -a

git status
git diff
