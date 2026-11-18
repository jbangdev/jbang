///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS build.sbt

import org.apache.commons.lang3.StringUtils;

public class App {
    public static void main(String... args) {
        System.out.println("sbt-classpath:" + StringUtils.reverse("hello"));
    }
}
