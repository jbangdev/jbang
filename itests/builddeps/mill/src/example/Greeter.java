package example;

import org.apache.commons.lang3.StringUtils;

/**
 * Illustrative source belonging to the real Mill module. Compiled by
 * {@code ./mill compile}; not on the classpath of the accompanying JBang
 * script — JBang only borrows the module's declared compile dependencies
 * via {@code //DEPS build.mill}.
 */
public class Greeter {
    public static String greet(String name) {
        return "Hello, " + StringUtils.capitalize(name) + "!";
    }
}
