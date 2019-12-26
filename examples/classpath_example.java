//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS log4j:log4j:1.2.17

import static java.lang.System.out;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import java.util.Arrays;

class classpath_example {

	static final Logger logger = Logger.getLogger(classpath_example.class);

	public static void main(String[] args) {
		BasicConfigurator.configure();
		logger.info("Welcome to jbang");

		Arrays.asList(args).forEach(arg -> logger.warn("arg: " + arg));
		logger.info("Hello from Java!");
	}
}