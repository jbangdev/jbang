///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS build.gradle
//SOURCES src/main/java/example/Greeter.java

public class AppSources {
    public static void main(String... args) {
        // //DEPS build.gradle borrows the project's dependencies (commons-lang3);
        // //SOURCES compiles the project's own source so we can use it too.
        System.out.println("gradle-greeter:" + example.Greeter.greet("world"));
    }
}
