package example;

import org.apache.commons.lang3.StringUtils;

/**
 * Illustrative source belonging to the real sbt project. Compiled by
 * {@code sbt compile}; not on the classpath of the accompanying JBang
 * script — JBang only borrows the project's declared compile dependencies
 * via {@code //DEPS build.sbt}.
 */
public class Greeter {
    public static String greet(String name) {
        return "Hello, " + StringUtils.capitalize(name) + "!";
    }
}
