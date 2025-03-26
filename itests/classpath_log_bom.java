///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.apache.logging.log4j:log4j-bom:2.24.3@pom
//DEPS org.apache.logging.log4j:log4j-api
//DEPS org.apache.logging.log4j:log4j-core

import static java.lang.System.out;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

class classpath_log_bom {

	static final Logger logger = LogManager.getLogger(classpath_log_bom.class);

	public static void main(String[] args) {
		Configurator.initialize(new DefaultConfiguration());
		Arrays.asList(args).forEach(arg -> out.println(arg));
	}
}