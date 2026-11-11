///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS pom.xml
//SOURCES src/main/java/example/Greeter.java

public class AppSources {
    public static void main(String... args) {
        // //DEPS pom.xml borrows the project's dependencies (commons-lang3);
        // //SOURCES compiles the project's own source so we can use it too.
        System.out.println("maven-greeter:" + example.Greeter.greet("world"));
    }
}
