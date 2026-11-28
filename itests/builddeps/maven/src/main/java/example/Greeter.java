package example;

import org.apache.commons.lang3.StringUtils;

/**
 * Illustrative source belonging to the real Maven project. This class is
 * compiled by {@code mvn compile}; it is not on the classpath of the
 * accompanying JBang script — JBang only borrows the project's declared
 * compile dependencies via {@code //DEPS pom.xml}.
 */
public class Greeter {
    public static String greet(String name) {
        return "Hello, " + StringUtils.capitalize(name) + "!";
    }
}
