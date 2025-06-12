///usr/bin/env jbang "$0" "$@" ; exit $?
//DESCRIPTION This is a description test
//DOCS ./this_do_not_exists.txt
//DOCS main=docsexample.java
//DOCS https://xam.dk/notthere
//DOCS javadoc=readme.md
//DOCS javadoc=/tmp/this_exists.txt
//DOCS http://www.jbang.dev/documentation/guide/latest/index.html
//DOCS does-not-exist.txt

import static java.lang.System.*;

public class docsexample {

    public static void main(String... args) {
        out.println("Hello " + (args.length>0?args[0]:"World"));
    }
}