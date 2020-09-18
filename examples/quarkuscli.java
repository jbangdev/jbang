//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-picocli:1.8.1.Final
//DEPS org.testcontainers:testcontainers:1.14.3
//DEPS org.testcontainers:mysql:1.14.3
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
package com.acme.picocli;

import picocli.CommandLine;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.QuarkusApplication;

@CommandLine.Command 
public class quarkuscli implements Runnable {

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


}

@Dependent
class GreetingService {
    void sayHello(String name) {
        System.out.println("Hello " + name + "!");
    }
}