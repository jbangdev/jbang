//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-resteasy:${q.v:1.8.1.Final}
//DEPS io.quarkus:quarkus-container-image-jib:${q.v:1.8.1.Final}
// //DEPS io.quarkus:quarkus-kubernetes:${q.v:1.8.1.Final}
//Q:CONFIG quarkus.container-image.name=quarkusjbangdemo

import io.quarkus.runtime.Quarkus;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/hello")
@ApplicationScoped
public class quarkusjib {

    @GET
    public String sayHello() {
        return "hello from Quarkus with jbang.dev";
    }

}
