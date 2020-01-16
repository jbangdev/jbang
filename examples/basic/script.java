///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.offbytwo:docopt:0.6.0.20150202,log4j:log4j:1.2.14

import java.util.Arrays;
import java.util.Scanner;

import static java.lang.System.*;

public class script {
 
    public static void main(String[] args) {
        out.println("Hello, Java scripts!");
        Arrays.stream(args)
            .forEach(arg -> System.out.println(arg));
        
        var xyz = new Scanner(in, "UTF-8");
        
        
        while (xyz.hasNextLine()) {
        	out.println(xyz.nextLine());
        }
        
    }
 
} 