//usr/bin/env jbang "$0" "$@" ; exit $?

//REPOS xamdk=https://xam.dk/maven
//DEPS io.quarkus:quarkus-resteasy:999-SNAPSHOT
//DEPS io.quarkus:quarkus-smallrye-openapi:999-SNAPSHOT
//DEPS io.quarkus:quarkus-swagger-ui:999-SNAPSHOT
//DEPS io.quarkus:quarkus-openshift:999-SNAPSHOT
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.swagger-ui.always-include=true
//Q:CONFIG quarkus.kubernetes.deploy=true

import io.quarkus.runtime.Quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/hello")
@ApplicationScoped
public class quarkus {

    @GET
    public String sayHello() {
        return "hello from Quarkus with jbang.dev";
    }

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
