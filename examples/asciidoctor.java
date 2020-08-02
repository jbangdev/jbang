//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.asciidoctor:asciidoctorj:2.3.1
//DEPS org.asciidoctor:asciidoctorj-diagram:2.0.2

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Callable;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.AsciiDocDirectoryWalker;

@Command(name = "asciidoctor", mixinStandardHelpOptions = true, version = "asciidoctor 0.1",
        description = "asciidoctor made with jbang")
class asciidoctor implements Callable<Integer> {

    @Parameters
    File directory;
    
    public static void main(String... args) {
        int exitCode = new CommandLine(new asciidoctor()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        Asciidoctor ad = Asciidoctor.Factory.create();

        ad.requireLibrary("asciidoctor-diagram");

        String[] result = ad.convertDirectory(new AsciiDocDirectoryWalker(directory.toString()),
                             new HashMap<String, Object>());

        Arrays.stream(result).forEach(System.out::println);

        return 0;
    }
}
