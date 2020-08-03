//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.9.8
//DEPS org.jboss.reasteasy:resteasy-client:4.5.2.Final
//DEPS org.jboss.reasteasy:resteasy-jackson2-provider:4.5.2.Final
//REPOS mavencentral, google, jcenter

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "resteasyclient", mixinStandardHelpOptions = true, version = "resteasyclient 0.1",
        description = "resteasyclient made with jbang")
class resteasyclient implements Callable<Integer> {

    @Parameters(index = "0", description = "The greeting to print", defaultValue = "World!")
    private String greeting;

    public static void main(String... args) {
        int exitCode = new CommandLine(new resteasyclient()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        System.out.println("Hello " + greeting);
        return 0;
    }
}
