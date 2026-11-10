///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS build.mill
//SOURCES src/example/Greeter.java

public class AppSources {
    public static void main(String... args) {
        // //DEPS build.mill borrows the project's dependencies (commons-lang3);
        // //SOURCES compiles the project's own source so we can use it too.
        System.out.println("mill-greeter:" + example.Greeter.greet("world"));
    }
}
