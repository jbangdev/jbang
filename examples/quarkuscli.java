//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-picocli:1.8.0.CR1
//DEPS org.testcontainers:testcontainers:1.14.3
//DEPS org.testcontainers:mysql:1.14.3
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
package com.acme.picocli;

import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.QuarkusApplication;

@QuarkusMain
@CommandLine.Command 
public class quarkuscli implements Runnable, QuarkusApplication {

    @CommandLine.Option(names = {"-n", "--name"}, description = "Who will we greet?", defaultValue = "World")
    String name;

    @Inject
    CommandLine.IFactory factory; 

    private final GreetingService greetingService;

    public quarkuscli(GreetingService greetingService) { 
        this.greetingService = greetingService;
    }

    @Override
    public void run() {
        greetingService.sayHello(name);
    }

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this, factory).execute(args);
    }

    public static void main(String... args) {
        io.quarkus.runtime.Quarkus.run(quarkuscli.class, args);
    }
}

@Dependent
class GreetingService {
    void sayHello(String name) {
        System.out.println("Hello " + name + "!");
    }
}