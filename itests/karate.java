///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS jitpack
// Using jitpack to get latest dev version of karate to be able
// to use the just added fork() methods to test clis.
// see https://github.com/intuit/karate/issues/1191
////DEPS com.github.intuit.karate:karate-netty:develop-SNAPSHOT
//DEPS com.github.intuit.karate:karate-netty:e2882c4
////DEPS com.intuit.karate:karate-netty:2.0.0
////DEPS com.github.maxandersen.karate:karate-netty:19e06766

class karate {

    public static void main(String... args) {
        com.intuit.karate.Main.main(args);
    }

 }