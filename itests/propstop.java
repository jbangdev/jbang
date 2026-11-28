//VARS greeting=hello
//PROPS my.greeting=${greeting} my.fixed=top
//SOURCES propschild.java

public class propstop {
    public static void main(String[] args) {
        System.out.println(System.getProperty("my.greeting"));
    }
}
