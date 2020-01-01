#!/usr/bin/env bash
#
#         name : aserta
#  description : a handy unit testing framework
#   repository : http://github.com/andamira/aserta
#       author : José Luis Cruz © 2016-2017
#                Robert Lehmann © 2009-2015 (assert.sh)
#      license : MIT & LGPLv3
_ASERTA_VERSION=2.2.1


#-------------------------------------------------------------------------------
# main
#
# shellcheck disable=SC2155
main() {

	export DISCOVERONLY="${DISCOVERONLY:-}"
	export DEBUG="${DEBUG:-}"
	export STOP="${STOP:-}"
	export INVARIANT="${INVARIANT:-}"
	export CONTINUE="${CONTINUE:-}"

	# Commands substitutions
	export __GREP=$(__gnuCmd grep)
	export __DATE=$(__gnuCmd date)

	_assert_reset
	: "${tests_suite_status:=0}"  # remember if any of the tests failed so far
	: "${tests_ran_total:=0}"	  # remember total number of tests (inc. failures)
	: "${tests_failed_total:=0}"  # remember the total number of test failures

	trap _assert_cleanup EXIT

	_indent=$'\n\t' # local format helper

	parseArguments "$@"

} # main()


#-------------------------------------------------------------------------------
# parseArguments
#
#   Parses the arguments received by the script and sets the pertinent options.
#
# Arguments:
#   $@ - all the arguments passed to the script
#
parseArguments() {
	local OPTIND option function
	local optspec=":cdivxh-:"

	# Show usage and exit if:
	# - the script has not been sourced
	# - no arguments has been passed
	[[ "$0" == "${BASH_SOURCE[0]}" && -z "$@" ]] && usage && exit 0

	# Parse the options

	while getopts "$optspec" option; do
		case "$option" in

			# long options
			# http://stackoverflow.com/a/7680682/940200

			-)
				case "$OPTARG" in
					continue)  CONTINUE=1      ;;
					discover)  DISCOVERONLY=1  ;;
					invariant) INVARIANT=1     ;;
					verbose)   DEBUG=1         ;;
					stop)      STOP=1          ;;

					help)      usage ; exit 0  ;;
					version)   version; exit 0 ;;

					*)  # error
						if [[ "$OPTERR" == 1 ]]; then
							printf 'Unknown option --%s\n' "$OPTARG" >&2
							exit 255
						fi ;;
				esac ;;

			# short options

			c) CONTINUE=1     ;;
			d) DISCOVERONLY=1 ;;
			i) INVARIANT=1    ;;
			v) DEBUG=1        ;;
			x) STOP=1         ;;

			h) usage false; exit 0 ;;

			*)  # error
				if [[ "$OPTERR" != 1 || "${optspec:0:1}" == ":" ]]; then
					printf "Non-option argument: '-%s'\n" "$OPTARG" >&2
					exit 255
				fi ;;
		esac
	done
	shift $((OPTIND-1))



	# Finally parse an optional test function

	function="$1"; shift
	case "$function" in

		# supported functions

		assert_raises) assert_raises                   "$1" "$2" ;;
		assert_success) assert_success                      "$1" ;;
		assert_failure) assert_failure                      "$1" ;;

		assert) assert                                 "$1" "$2" ;;
		assert_startswith) assert_startswith           "$1" "$2" ;;
		assert_endswith) assert_endswith               "$1" "$2" ;;
		assert_contains) assert_contains               "$1" "$2" ;;
		assert_NOTcontains) assert_NOTcontains         "$1" "$2" ;;
		assert_matches) assert_matches                 "$1" "$2" ;;

		assert_str_equals) assert_str_equals           "$1" "$2" ;;
		assert_str_NOTequals) assert_str_NOTequals     "$1" "$2" ;;
		assert_str_startswith) assert_str_startswith   "$1" "$2" ;;
		assert_str_endswith) assert_str_endswith       "$1" "$2" ;;
		assert_str_contains) assert_str_contains       "$1" "$2" ;;
		assert_str_NOTcontains) assert_str_NOTcontains "$1" "$2" ;;
		assert_str_matches) assert_str_matches         "$1" "$2" ;;

		# unsupported functions

		assert_end|skip|skip_if)
			printf "Test function '%s' not supported as a script argument\n" \
				"$function"
			exit 254 ;;

		'') ;; # no function

		*)  # error
			printf "Unknown test function: '%s'\n" "$function"
			exit 255 ;;
	esac

} # parseArgs()


#-------------------------------------------------------------------------------
# usage
#
#   Print usage information
#
usage() {

	cat <<- ENDUSAGE
	Usage: $(basename "$0") [options] [assert_function] [arguments]

	> a handy unit testing framework <

	OPTION FLAGS

	  -v, --verbose    generate output for every individual test case
	  -x, --stop       stop running tests after the first failure
	  -i, --invariant  do not measure timings to remain invariant between runs
	  -d, --discover   collect test suites only, do not run any tests
	  -c, --continue   do not modify exit code to test suite status

	  -h, --help       display this help and exit
	      --version    show version info and exit
	ENDUSAGE

} # usage()


#-------------------------------------------------------------------------------
# version
#
#   Print version information
#
version() {

	cat <<- ENDVERSION
	aserta v$_ASERTA_VERSION

	<https://github.com/andamira/aserta>

	Copyright (C) 2016-2017 José Luis Cruz
	  Released under the MIT license.
	Copyright (C) 2009-2015 Robert Lehmann
	  Released under the LGPLv3 license

	Dependencies found:
	  $__GREP, $__DATE

	ENDVERSION

} #version()


#-------------------------------------------------------------------------------
# __gnuCmd
#
#   Returns the resolved command, preferring the GNU versions
#   and falling back to the standard version when not found.
#
__gnuCmd() {
	local default_cmd="$1"
	local gnu_cmd="g$default_cmd"

	if [[ "$(which "$gnu_cmd" 2> /dev/null)" ]]; then
		printf '%s' "$gnu_cmd"
	else
		printf '%s' "$default_cmd"
	fi

} #__gnuCmd()


#-------------------------------------------------------------------------------
# _assert_cleanup
#
#   gets called on script exit
#
_assert_cleanup() {
	local status=$?

	# modify exit code if it's not already non-zero
	[[ $status -eq 0 && -z $CONTINUE ]] && exit "$tests_suite_status"

} # _assert_cleanup()


#-------------------------------------------------------------------------------
# _assert_fail <failure> <command> <stdin>
#
_assert_fail() {
	[[ -n "$DEBUG" ]] && printf 'X'
	report="test #$tests_ran \"$2${3:+ <<< $3}\" failed:${_indent}$1"
	if [[ -n "$STOP" ]]; then
		[[ -n "$DEBUG" ]] && printf '\n'
		printf '%s\n' "$report"
		exit 1
	fi
	tests_errors[$tests_failed]="$report"
	(( tests_failed++ )) || :
	return 1

} # _assert_fail()


#-------------------------------------------------------------------------------
# _assert_reset
#
_assert_reset() {
	tests_ran=0
	tests_failed=0
	tests_errors=()
	tests_starttime="$($__DATE +%s%N)" # nanoseconds_since_epoch

} # _assert_reset()


#-------------------------------------------------------------------------------
# _assert_with_grep <grep modifiers> <string> <pattern>
#
# NOTE: doesn't support newlines in the pattern
#
_assert_with_grep() {
	local modifier="$1"
	local string="$2"
	local pattern="$3"

	assert_success "printf '$string' | $__GREP $modifier '$pattern'" || return 1

} # _assert_with_grep()



# TEST FUNCTIONS: FLOW CONTROL
# ============================


#-------------------------------------------------------------------------------
# skip_if <command ..>
#
skip_if() {
	(eval "$@") > /dev/null 2>&1 && status=0 || status="$?"
	[[ "$status" -eq 0 ]] || return
	skip

} # skip_if()


#-------------------------------------------------------------------------------
# skip (no arguments)
#
skip() {
	shopt -q extdebug && tests_extdebug=0 || tests_extdebug=1
	shopt -q -o errexit && tests_errexit=0 || tests_errexit=1

	# enable extdebug so returning 1 in a DEBUG trap handler skips next command
	shopt -s extdebug

	# disable errexit (set -e) so we can safely return 1 without causing exit
	set +o errexit
	tests_trapped=0
	trap _skip DEBUG

} # skip()


#-------------------------------------------------------------------------------
# _skip
#
_skip() {
	if [[ $tests_trapped -eq 0 ]]; then
		# DEBUG trap for command we want to skip. Do not remove the handler
		# yet because *after* the command we need to reset extdebug/errexit
		# (in another DEBUG trap).
		tests_trapped=1
		[[ -z "$DEBUG" ]] || printf 's'
		return 1
	else
		trap - DEBUG
		[[ $tests_extdebug -eq 0 ]] || shopt -u extdebug
		[[ $tests_errexit -eq 1 ]] || set -o errexit
		return 0
	fi

} # _skip()


#-------------------------------------------------------------------------------
# assert_end [suite ..]
#
assert_end() {
	tests_endtime="$($__DATE +%s%N)"

	# required visible decimal place for seconds (leading zeros if needed)
	local tests_time; tests_time="$( printf "%010d" \
		"$(( ${tests_endtime/%N/000000000}
			- ${tests_starttime/%N/000000000} ))")"  # in ns
	tests="$tests_ran ${*:+$* }tests"

	[[ -n "$DISCOVERONLY" ]] && printf 'collected %s.\n' "$tests" \
		&& _assert_reset && return
	[[ -n "$DEBUG" ]] && printf '\n'

	# to get report_time split tests_time on 2 substrings:
	#   ${tests_time:0:${#tests_time}-9} - seconds
	#   ${tests_time:${#tests_time}-9:3} - milliseconds
	if [[ -z "$INVARIANT" ]]; then
		report_time=" in ${tests_time:0:${#tests_time}-9}.${tests_time:${#tests_time}-9:3}s"
	else report_time=; fi

	if [[ "$tests_failed" -eq 0 ]]; then
		printf 'all %s passed%s.\n' "$tests" "$report_time"
	else
		for error in "${tests_errors[@]}"; do printf '%s\n' "$error"; done
		printf '%d of %s failed%s.\n' "$tests_failed" "$tests" "$report_time"
	fi

	tests_ran_total=$((tests_ran_total + tests_ran))
	tests_failed_total=$((tests_failed_total + tests_failed))
	[[ $tests_failed -gt 0 ]] && tests_suite_status=1
	_assert_reset

} # assert_end()



# TEST FUNCTIONS: RETURN STATUS
# =============================


#-------------------------------------------------------------------------------
# assert_raises <command> <expected code> [stdin]
#
assert_raises() {
	(( tests_ran++ )) || :
	[[ -z "$DISCOVERONLY" ]] || return
	status=0
	(eval "$1" <<< "${3:-}") > /dev/null 2>&1 || status="$?"
	expected=${2:-0}
	if [[ "$status" -eq "$expected" ]]; then
		[[ -z "$DEBUG" ]] || printf '.'
		return
	fi
	_assert_fail "program terminated with code $status instead of $expected" \
		"$1" "$3"

} # assert_raises()


#-------------------------------------------------------------------------------
# assert_success <command> [stdin]
#
assert_success() {
	assert_raises "$1" 0 "${2:-}"
}


#-------------------------------------------------------------------------------
# assert_failure <command> [stdin]
#
assert_failure() {
	(( tests_ran++ )) || :
	[[ -z "$DISCOVERONLY" ]] || return
	status=0
	(eval "$1" <<< "${2:-}") > /dev/null 2>&1 || status="$?"
	if [[ "$status" != "0" ]]; then
		[[ -z "$DEBUG" ]] || printf '.'
		return
	fi
	_assert_fail "program terminated with a zero return code; expecting non-zero return code" \
		"$1" "$2"

} # assert_failure()



# TEST FUNCTIONS: EXPECTED OUTPUT
# ===============================


#-------------------------------------------------------------------------------
# assert <command> <expected stdout> [stdin]
#
assert() {
	(( tests_ran++ )) || :
	[[ -z "$DISCOVERONLY" ]] || return
	# shellcheck disable=SC2059
	expected=$(printf "${2:-}")
	# shellcheck disable=SC2059
	result=$(printf "$(eval 2>/dev/null "$1" <<< "${3:-}")")
	if [[ "$result" == "$expected" ]]; then
		[[ -z "$DEBUG" ]] || printf '.'
		return
	fi
	result="$(sed -e :a -e '$!N;s/\n/\\n/;ta' <<< "$result")"
	[[ -z "$result" ]] && result="nothing" || result="\"$result\""
	[[ -z "$2" ]] && expected="nothing" || expected="\"$2\""
	_assert_fail "expected $expected${_indent}got $result" "$1" "${3:-}"

} # assert()


#-------------------------------------------------------------------------------
# assert_contains <command> <expected part of STDOUT>
#
assert_contains() {
	assert_success "[[ '$($1)' == *'$2'* ]]"
}

#-------------------------------------------------------------------------------
# assert_NOTcontains <command> <not expected part of STDOUT>
#
assert_NOTcontains() {
	assert_success "[[ '$($1)' != *'$2'* ]]"
}


#-------------------------------------------------------------------------------
# assert_startswith <command> <expected start of STDOUT>
#
assert_startswith() {
	assert_success "[[ '$($1)' == '$2'* ]]"
}


#-------------------------------------------------------------------------------
# assert_endswith <command> <expected end of STDOUT>
#
assert_endswith() {
	assert_success "[[ '$($1)' == *'$2' ]]"
}


#-------------------------------------------------------------------------------
# assert_matches <command> <expected matching pattern>
#
assert_matches() {
	_assert_with_grep '-E' "$($1)" "$2"
}



# TEST FUNCTIONS: STRING COMPARISON
# ============================


#-------------------------------------------------------------------------------
# assert_str_equals <string> <expected string>
#
assert_str_equals() {
	assert_success "[[ '$1' == '$2' ]]"
}


#-------------------------------------------------------------------------------
# assert_str_NOTequals <string> <not expected string>
#
assert_str_NOTequals() {
	assert_success "[[ '$1' != '$2' ]]"
}


#-------------------------------------------------------------------------------
# assert_str_startswith <string> <expected start to the string>
#
assert_str_startswith() {
	assert_success "[[ '$1' == '$2'* ]]"
}


#-------------------------------------------------------------------------------
# assert_str_endswith <command> <expected end to the string>
#
assert_str_endswith() {
	assert_success "[[ '$1' == *'$2' ]]"
}


#-------------------------------------------------------------------------------
# assert_str_contains <command> <expected part of the string>
#
assert_str_contains() {
	assert_success "[[ '$1' == *'$2'* ]]"
}

#-------------------------------------------------------------------------------
# assert_str_NOTcontains <string> <not expected part of the string>
#
assert_str_NOTcontains() {
	assert_success "[[ '$1' != *'$2'* ]]"
}


#-------------------------------------------------------------------------------
# assert_str_matches <string> <expected matching pattern>
#
assert_str_matches() {
	_assert_with_grep '-E' "$1" "$2"
}


main "$@"
