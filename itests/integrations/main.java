///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 13+
//DEPS integration.java
//RUNTIME_OPTIONS -Dfoo=fubar
//MY:TEST aap
//CDS

public class main {

    public static void main(String... args) {
        System.out.println("Wrong main!!!");
    }
}

class altmain {

    public static void main(String... args) {
        System.out.println("Hello World!");
        System.out.println("foo: " + System.getProperty("foo"));
        System.out.println("bar: " + System.getProperty("bar"));
    }
}
