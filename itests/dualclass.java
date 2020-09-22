///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

import static java.lang.System.*;

class firstclass {

}

public class dualclass {

    public static void main(String... args) {
        out.println("Hello " + (args.length>0?args[0]:"World"));
    }
}
