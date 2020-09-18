//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-resteasy:1.8.0.CR1
//DEPS io.quarkus:quarkus-smallrye-openapi:1.8.0.CR1
//DEPS io.quarkus:quarkus-swagger-ui:1.8.0.CR1
//DEPS io.quarkus:quarkus-container-image-jib:1.8.0.CR1
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
//Q:CONFIG quarkus.swagger-ui.always-include=true
//Q:CONFIG quarkus.container-image.name=quarkusjbangdemo
//FILES META-INF/resources/index.html=index.html

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
