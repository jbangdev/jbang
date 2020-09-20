//usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//SOURCES Two.java

import static java.lang.System.*;

public class one {

    public static void main(String... args) {
        out.println(Two.class.getName());
    }
}
