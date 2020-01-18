//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview

import static java.lang.System.*;

public class records {

    record Point(int x, int y) {}

    public static void main(String[] args) {
        var p = new Point(2,4);
        out.println(p);
    }
}