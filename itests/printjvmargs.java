///usr/bin/env jbang "$0" "$@" ; exit $?

import java.lang.management.ManagementFactory;
import java.util.List;

public class printjvmargs {
    public static void main(String... args) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : jvmArgs) {
            System.out.println("JVMARG:" + arg);
        }
        System.out.println("DONE");
    }
}
