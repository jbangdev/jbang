//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS https://github.com/kohsuke/github-api/tree/github-api-1.103
import static java.lang.System.out;
import static java.util.Arrays.*;
import org.kohsuke.github.*;

class urldep {

    public static void main(String[] args) throws Exception {

        GitHub github = GitHub.connectAnonymously();

        GHRepository ghRepo = github.getRepository("jbangdev/jbang");

        out.println(ghRepo);
    }
}