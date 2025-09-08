#!/bin/bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env

./gradlew clean
./gradlew spotlessApply
./gradlew assemble
./gradlew installDist

ls -l build/install/jbang/bin

./build/install/jbang/bin/jbang version
