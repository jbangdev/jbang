///usr/bin/env jbang "$0" "$@" ; exit $?
///DEPS com.github.lalyos:jfiglet:0.0.9

package extending;

public class Foo extends Bar {
    public static void main(String... args) {
        System.out.println("hello JBang");
    }
}
