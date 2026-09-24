///usr/bin/env jbang "$0" "$@" ; exit $?
//CONF logback.file.name=app.log
//CONF logback.file.maxSize=1MB
//CONF spring.http.client.connect-timeout=5s
//CONF spring.http.client.read-timeout=10s
//CONF spring.http.client.read-timeout=20s

import java.lang.management.ManagementFactory;
import java.util.List;

public class confoptions {
    public static void main(String... args) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : jvmArgs) {
            System.out.println("JVMARG:" + arg);
        }
        System.out.println("PROP:logback.file.name=" + System.getProperty("logback.file.name"));
        System.out.println("PROP:logback.file.maxSize=" + System.getProperty("logback.file.maxSize"));
        System.out.println("PROP:spring.http.client.connect-timeout=" + System.getProperty("spring.http.client.connect-timeout"));
        System.out.println("PROP:spring.http.client.read-timeout=" + System.getProperty("spring.http.client.read-timeout"));
        System.out.println("DONE");
    }
}
