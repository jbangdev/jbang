///usr/bin/env jbang "$0" "$@" ; exit $?

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import java.util.Arrays;

import groovy.lang.Grab;
import groovy.lang.Grapes;
import groovy.lang.GrabResolver;

@GrabResolver("central")
@Grapes({
    @Grab(group="ch.qos.reload4j", module="reload4j", version="[1.2.18,1.2.19)"),
    @Grab(group = "org.apache.groovy", module = "groovy", version = "4.0.0"),
})
class classpath_log_grab_with_ranges {

    static final Logger logger = Logger.getLogger(classpath_log_grab_with_ranges.class);

    public static void main(String[] args) {
        BasicConfigurator.configure();
        logger.info("Welcome to jbang");

        Arrays.asList(args).forEach(arg -> logger.warn("arg: " + arg));
        logger.info("Hello from Java!");
    }
}
