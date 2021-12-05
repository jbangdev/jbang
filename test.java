///usr/bin/env jbang "$0" "$@" ; exit $?
// Update the Quarkus version to what you want here or run jbang with
// `-Dquarkus.version=<version>` to override it.
//DEPS io.quarkus:quarkus-bom:${quarkus.version:2.2.3.Final}@pom
//DEPS io.quarkus:quarkus-resteasy
// //DEPS io.quarkus:quarkus-smallrye-openapi
// //DEPS io.quarkus:quarkus-swagger-ui
//JAVAC_OPTIONS -parameters

import io.quarkus.runtime.Quarkus;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import io.quarkus.runtime.StartupEvent;
import javax.enterprise.event.Observes;


@Path("/hello")
@ApplicationScoped
public class test {

    @GET
    public String sayHello() {
        return "Hello from Quarkus with jbang.dev";
    }

    
    void onStart(@Observes StartupEvent ev) throws Exception {
        System.out.println(io.quarkus.runtime.LaunchMode.current());
        java.nio.file.Files.writeString(java.nio.file.Path.of("mode.quarkus"), io.quarkus.runtime.LaunchMode.current().toString());
        Quarkus.asyncExit(0);
    }
}
