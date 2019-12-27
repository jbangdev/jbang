//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.kohsuke:github-api:1.101

import static java.lang.System.out;
import static java.util.Arrays.*;
import org.kohsuke.github.*;

class githubinfo {

    public static void main(String[] args) throws Exception {

        GitHub github = GitHub.connectAnonymously();

        var ghRepo = github.getRepository("maxandersen/jbang");

        out.println(ghRepo);
    }
}