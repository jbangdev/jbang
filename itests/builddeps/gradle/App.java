///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS build.gradle

import org.apache.commons.lang3.StringUtils;

public class App {
    public static void main(String... args) {
        System.out.println("gradle-classpath:" + StringUtils.reverse("hello"));
    }
}
