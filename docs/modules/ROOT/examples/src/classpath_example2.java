///usr/bin/env jbang "$0" "$@" ; exit $?

import static java.lang.System.out;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import java.util.Arrays;

// <.>
import groovy.lang.Grab;
import groovy.lang.Grapes;
import groovy.lang.GrabResolver;

// <.>
@GrabResolver("central")
// <.>
@Grapes({
    // <.>
    @Grab(group = "ch.qos.reload4j", module = "reload4j", version = "1.2.26"),
    // <.>
    @Grab(group = "org.apache.groovy", module = "groovy", version = "4.0.28"),
})

class classpath_example2 {

  static final Logger logger = Logger.getLogger(classpath_example2.class);

  public static void main(String[] args) {
    BasicConfigurator.configure();
    Arrays.asList(args).forEach(out::println);
  }
}
