///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//JAVA_OPTIONS -Dfoo=bar "-Dbar=aap noot mies"

import static java.lang.System.*;

public class helloworld {

    public static void main(String... args) {
        out.println("Hello " + (args.length>0?args[0]:"World"));
    }
}
