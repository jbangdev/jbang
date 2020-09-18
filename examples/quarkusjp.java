//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.maxandersen.quarkus:quarkus-resteasy:jbangenabled-SNAPSHOT
//DEPS io.quarkus:quarkus-smallrye-openapi:999-SNAPSHOT
//DEPS io.quarkus:quarkus-swagger-ui:999-SNAPSHOT
//CONFIG quarkus.swagger-ui.always-include=true


import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static java.lang.System.*;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
@Path("/hello")
public class quarkusjp {

    public static void main(String... args) {
        Quarkus.run(args);
    }

    @GET
    public String sayHello() {
        return "hello from Quarkus with jbang.dev";
    }

}
