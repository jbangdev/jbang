open := if os() == "macos" { "open" } else { "xdg-open" }

@default:
    just --choose

# build without tests
build:
    gradle spotlessApply installDist -x test

# run tests
test:
    gradle test

preitest := if path_exists('build/install/jbang/bin') != 'true' {
  'gradle installDist -x test'
} else {
    ''
}

# open test report 
opentest:
    {{open}} build/reports/tests/test/index.html

# run integration tests
itest:
    {{preitest}}
    @cd itests && ./itests.sh

# open integeration test report
openitest:
    {{open}} build/karate/surefire-reports/karate-summary.html
