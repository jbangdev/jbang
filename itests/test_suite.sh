#!/usr/bin/env bash
#/bin/bash -x

#set -e

export DEBUG=1

. aserta

export SCRATCH=`mktemp -d -t "$(basename $0).XXX"`
echo "Using $SCRATCH as scratch dir"
# deletes the temp directory
function cleanup {
  if [ -z ${KEEP_SCRATCH+x} ]; then
    rm -rf "$SCRATCH"
    echo "Deleted scratch dir $SCRATCH"
   else
    echo "Kept scratch dir: $SCRATCH"
   fi
}

# register the cleanup function to be called on the EXIT signal
trap cleanup EXIT

## define test helper, see https://github.com/lehmannro/assert.sh/issues/24
assert_statement(){
    # usage cmd exp_stout exp_stder exp_exit_code
    assert "$1" "$2"
    assert "( $1 ) 2>&1 >/dev/null" "$3"
    assert_raises "$1" "$4"
}
#assert_statement "echo foo; echo bar  >&2; exit 1" "foo" "bar" 1


assert_stderr(){
    assert "( $1 ) 2>&1 >/dev/null" "$2"
}

#assert_stderr "echo foo" "bar"

#http://stackoverflow.com/questions/3005963/how-can-i-have-a-newline-in-a-string-in-sh
#http://stackoverflow.com/questions/3005963/how-can-i-have-a-newline-in-a-string-in-sh
export NL=$'\n'

echo "Cleaning JBANG_CACHE"
rm -rf ~/.jbang

## init ##
assert "jbang --init $SCRATCH/test.java"
assert_raises "test -f $SCRATCH/test.java" 0

assert "jbang $SCRATCH/test.java" "Hello World"

assert_raises "rm $SCRATCH/test.java" 0

assert "jbang helloworld.java jbangtest" "Hello jbangtest"

java -version 2>&1 >/dev/null| grep version | grep "1.8" >/dev/null
JAVA8=$?

case "$JAVA8" in
  1) # jsh should also work
     assert "jbang helloworld.jsh" "Hello World"
     # jsh should also get parameters
     assert "jbang helloworld.jsh JSH!" "Hello JSH!" ;;
  *) echo "Java 8 installed - skipping jsh"
esac

## test dependency resolution on custom local repo (to avoid conflicts with existing ~/.m2)
export JBANG_REPO=$SCRATCH/testrepo
assert_stderr "jbang classpath_log.java" "[jbang] Resolving dependencies...\n[jbang]     Resolving log4j:log4j:1.2.17...Done\n[jbang] Dependencies resolved\n[jbang] Building jar..."
assert_raises "test -d $SCRATCH/testrepo" 0
assert "grep -c $SCRATCH/testrepo ~/.jbang/dependency_cache.txt" 1
# run it 2nd time and no resolution should happen
assert_stderr "jbang classpath_log.java" ""

## test urls
assert "jbang https://raw.githubusercontent.com/maxandersen/jbang/master/examples/helloworld.java viaurl" "Hello viaurl"

## test that can figure out main class with dual classes
assert "jbang dualclass.java" "Hello World"

### Cleanup
rm RESULTS
assert_end jbang > RESULTS

## work around to get error code happening
cat RESULTS
exit `grep -c "failed" RESULTS`
