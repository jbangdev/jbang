///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS log4j:log4j:1.2.17

import org.apache.log4j.Logger
import org.apache.log4j.BasicConfigurator

fun main(args: Array<String>) {
   args.forEach { println(it) }
}

// gradle run --args="1 2 3"
