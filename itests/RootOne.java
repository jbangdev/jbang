///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//SOURCES Two.java
//SOURCES nested/*.java

import static java.lang.System.*;

public class RootOne {

    public static void main(String... args)
    {
        out.println(nested.NestedOne.class.getName());
        out.println(nested.NestedTwo.class.getName());
    }
}
