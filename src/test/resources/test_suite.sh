#!/usr/bin/env bash
#/bin/bash -x

export DEBUG="--verbose"

. aserta


## define test helper, see https://github.com/lehmannro/assert.sh/issues/24
assert_statement(){
    # usage cmd exp_stout exp_stder exp_exit_code
    assert "$1" "$2"
    assert "( $1 ) 2>&1 >/dev/null" "$3"
    assert_raises "$1" "$4"
}
#assert_statment "echo foo; echo bar  >&2; exit 1" "foo" "bar" 1


assert_stderr(){
    assert "( $1 ) 2>&1 >/dev/null" "$2"
}
#assert_stderr "echo foo" "bar"

#http://stackoverflow.com/questions/3005963/how-can-i-have-a-newline-in-a-string-in-sh
#http://stackoverflow.com/questions/3005963/how-can-i-have-a-newline-in-a-string-in-sh
export NL=$'\n'

## init ##
assert "jbang --init test.java"
assert_raises "test -f test.java" 0

assert "jbang test.java" "Hello World"

sed -ie "s/World..;/\" + (args.length > 0 ? args[0] : \"World\"));/g" test.java
assert "jbang test.java jbangtest" "Hello jbangtest"

JBANG_HOME=testrepo
assert_contains "jbang classpath_log.java" "Welcome to jbang"
assert_raises "test -f testrepo" 0


rm test.java

assert_end jbang
