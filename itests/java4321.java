///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//JAVA 4321

import static java.lang.System.*;

public class java4321 {

    public static void main(String... args) {
        out.println("java:" + System.getProperty("java.version"));
    }
}
