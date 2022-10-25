///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//NATIVE_OPTIONS -O1
//RUNTIME_OPTIONS -Dfoo=bar "-Dbar=aap noot mies"
//MANIFEST foo bar=baz baz=${bazprop:nada}

import static java.lang.System.*;

public class helloworld {

    public static void main(String... args) {
        out.println("Hello " + (args.length>0?args[0]:"World"));
    }
}
