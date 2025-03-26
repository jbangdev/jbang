///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS log4j:log4j:1.2.17

import org.apache.log4j.Logger
import org.apache.log4j.BasicConfigurator

class classpath_log {

    companion object {
        private val logger: Logger = Logger.getLogger(classpath_log::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            BasicConfigurator.configure()
            args.forEach { println(it) }
        }
    }
}

// gradle run --args="1 2 3"
