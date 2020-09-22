///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

import static java.lang.System.*;

public class echo {

    public static void main(String... args) {
        for(int i = 0; i<args.length; i++) {
            System.out.println(i + ":" + args[i]);
        }
    }
}
