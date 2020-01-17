//usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static java.lang.System.in;
import static java.lang.System.out;

public class piping {

    public static void main(String... args) throws Exception {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            br.lines().forEach(line -> out.println("in: " + line));
        }
    }
}
