open := if os() == "macos" { "open" } else if os() == "windows" { "start" } else { "xdg-open" }

#@default:
#    just --choose

# build without tests
build:
    ./gradlew spotlessApply installDist -x test

format:
    ./gradlew spotlessApply

# run tests
test:
    ./gradlew test

preitest := if path_exists('build/install/jbang/bin') != 'true' {
    './gradlew spotlessApply installDist -x test'
} else {
    ''
}

# open test report 
opentest:
    {{open}} build/reports/tests/test/index.html

# run integration tests
itest:
    {{preitest}}
    ./gradlew clean integrationTest

# open shell with latest build in path
jbang *args:
    PATH="build/install/jbang/bin:$PATH" jbang {{args}}

# open integeration test report
openitest:
    {{open}} build/reports/allure-report/allureReport/index.html

# tag minor
tagminor:
    git commit --allow-empty -m "[minor] release"
    ./gradlew tag

tagpatch:
    git commit --allow-empty -m "[patch] release"
    ./gradlew tag

itestreport: # todo: should not be needed to clean
    -./gradlew clean integrationTest
    jbang allure@jbanghub/allure serve
