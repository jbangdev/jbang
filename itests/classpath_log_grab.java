//usr/bin/env jbang "$0" "$@" ; exit $?

import static java.lang.System.out;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import java.util.Arrays;

import groovy.lang.Grab;
import groovy.lang.Grapes;

@Grapes({
		@Grab(group="org.codehaus.groovy", module="groovy", version="2.5.8"),
		@Grab(module = "log4j", group = "log4j", version = "1.2.17")
})
class classpath_example {

	static final Logger logger = Logger.getLogger(classpath_example.class);

	public static void main(String[] args) {
		BasicConfigurator.configure();
		Arrays.asList(args).forEach(arg -> out.println(arg));
	}
}
