//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.platform:junit-platform-console-standalone:1.6.2

import org.junit.platform.console.ConsoleLauncher;

import static java.lang.System.*;

public class JunitRun {

    public static void main(String... args) {
        ConsoleLauncher.main(args);
    }
}
