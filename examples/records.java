//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview --release 14
//JAVA_OPTIONS --enable-preview

import static java.lang.System.*;

public class records {

    record Point(int x, int y) {}

    public static void main(String[] args) {
        var p = new Point(2,4);
        out.println(p);
    }
}
