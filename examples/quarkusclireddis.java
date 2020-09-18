//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-redis-client:${quarkus.version:1.8.1.Final}
//DEPS io.quarkus:quarkus-resteasy-mutiny:${quarkus.version:1.8.1.Final}
//DEPS io.quarkus:quarkus-resteasy-jsonb:${quarkus.version:1.8.1.Final}
//DEPS org.testcontainers:testcontainers:1.14.3
//Q:CONFIG quarkus.redis.hosts=localhost:6379

import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class quarkusclireddis  {

    static public class Increment {
        public String key;
        public int value;

        public Increment(String key, int value) {
            this.key = key;
            this.value = value;
        }

        public Increment() {
        }
    }

    @Singleton
    static public class IncrementService {

        @Inject
        RedisClient redisClient;

        @Inject
        ReactiveRedisClient reactiveRedisClient;

        public IncrementService() {

        }

        Uni<Void> del(String key) {
            return reactiveRedisClient.del(Arrays.asList(key))
                    .map(response -> null);
        }

        String get(String key) {
            return redisClient.get(key).toString();
        }

        void set(String key, Integer value) {
            redisClient.set(Arrays.asList(key, value.toString()));
        }

        void increment(String key, Integer incrementBy) {
            redisClient.incrby(key, incrementBy.toString());
        }

        Uni<List<String>> keys() {
            return reactiveRedisClient
                    .keys("*")
                    .map(response -> {
                        List<String> result = new ArrayList<>();
                        for (Response r : response) {
                            result.add(r.toString());
                        }
                        return result;
                    });
        }
    }

    @Path("/increments")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    static public class IncrementResource {

        @Inject
        IncrementService service;

        @GET
        public Uni<List<String>> keys() {
            return service.keys();
        }

        @POST
        public Increment create(Increment increment) {
            service.set(increment.key, increment.value);
            return increment;
        }

        @GET
        @Path("/{key}")
        public Increment get(@PathParam("key") String key) {
            return new Increment(key, Integer.valueOf(service.get(key)));
        }

        @PUT
        @Path("/{key}")
        public void increment(@PathParam("key") String key, Integer value) {
            service.increment(key, value);
        }

        @DELETE
        @Path("/{key}")
        public Uni<Void> delete(@PathParam("key") String key) {
            return service.del(key);
        }
    }

    public static void main(String... args) {
        GenericContainer redis = null;
        try {
            redis = new FixedHostPortGenericContainer("redis:3-alpine")
                    .withFixedExposedPort(6379, 6379);
            redis.start();

            io.quarkus.runtime.Quarkus.run(args);
        } finally {
            redis.stop();
    }
    }
}
