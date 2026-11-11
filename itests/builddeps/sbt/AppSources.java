///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS build.sbt
//SOURCES src/main/java/example/Greeter.java

public class AppSources {
    public static void main(String... args) {
        // //DEPS build.sbt borrows the project's dependencies (commons-lang3);
        // //SOURCES compiles the project's own source so we can use it too.
        System.out.println("sbt-greeter:" + example.Greeter.greet("world"));
    }
}
