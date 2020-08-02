//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.apache.camel:camel-core:3.0.1
//DEPS org.apache.camel:camel-main:3.0.1
//DEPS org.slf4j:slf4j-nop:1.7.25

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import static java.lang.System.*;

@Command(name = "camel", mixinStandardHelpOptions = true, version = "camel 0.1",
        description = "camel made with jbang")
class camel implements Callable<Integer> {

    @Parameters(index = "0", description = "The greeting to print", defaultValue = "World!")
    private String greeting;

    public static void main(String... args) {
        int exitCode = new CommandLine(new camel()).execute(args);
        exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        Main main = new Main();

        main.addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:test?period=1000")
                        .process(e -> out.println("Hello " + greeting));
            }
        });

        main.run();

        return 0;
    }
}
