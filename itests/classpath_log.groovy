///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS log4j:log4j:1.2.17

import org.apache.log4j.Logger
import org.apache.log4j.BasicConfigurator

class ClasspathExample {

    static final Logger logger = Logger.getLogger(ClasspathExample)

    static void main(String[] args) {
        BasicConfigurator.configure()
        args.each { arg -> println(arg) }
    }
}
