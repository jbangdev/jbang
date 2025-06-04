///usr/bin/env jbang "$0" "$@" ; exit $?

import static java.lang.System.*;

public class docstest_nodocs {
    public static void main(String... args) {
        out.println("Docs test without any DOCS directive");
    }
}
