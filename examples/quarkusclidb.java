//usr/bin/env jbang "$0" "$@" ; exit $?
/**
 * Run this with `jbang -Dquarkus.container-image.build=true build quarkus.java`
 * and it builds a docker image.
 */
//DEPS io.quarkus:quarkus-picocli:${q.v:1.8.1.Final}
//DEPS io.quarkus:quarkus-hibernate-orm-panache:${q.v:1.8.1.Final}
//DEPS io.quarkus:quarkus-jdbc-postgresql:${q.v:1.8.1.Final}
//DEPS org.testcontainers:postgresql:1.14.3
//DEPS org.postgresql:postgresql:42.1.4
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.datasource.db-kind=postgresql
//Q:CONFIG quarkus.datasource.username=sarah
//Q:CONFIG quarkus.datasource.password=connor
//Q:CONFIG quarkus.datasource.jdbc.url=jdbc:postgresql:databasename
//Q:CONFIG quarkus.hibernate-orm.database.generation=drop-and-create

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.persistence.Entity;
import javax.transaction.Transactional;

import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.QuarkusApplication;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@QuarkusMain
@CommandLine.Command 
public class quarkusclidb implements Runnable, QuarkusApplication {

    @CommandLine.Option(names = {"-n", "--name"}, description = "Who will we greet?", defaultValue = "World")
    String name;

    @Inject
    CommandLine.IFactory factory; 

    private final GreetingService greetingService;

    public quarkusclidb(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @Override
    @ActivateRequestContext
    @Transactional
    public void run() {
       greetingService.sayHello(name);
    }

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this, factory).execute(args);
    }

    public static void main(String... args) {
        int hostPort = 5432;
        int containerExposedPort = 5432;

        Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding((Ports.Binding.bindPort(hostPort)),new ExposedPort(containerExposedPort)));

        var dbcontainer = new PostgreSQLContainer("postgres:11.1").withDatabaseName("databasename")
        .withUsername("sarah").withPassword("connor").withCreateContainerCmdModifier(cmd);

        dbcontainer.start();
        io.quarkus.runtime.Quarkus.run(quarkusclidb.class, args);
    }

    @Entity
    static public class Person extends PanacheEntity {
        public String name;
        public LocalDate birth;
        public String status;

        public static Person findByName(String name){
            return find("name", name).firstResult();
        }

        public static List<Person> findAlive(){
            return list("status", "alive");
        }

        public static void deleteStefs(){
            delete("name", "Stef");
        }
    }

    @Dependent
    static public class GreetingService {
        void sayHello(String name) {

            // creating a person
            Person person = new Person();
            person.name = "Stef";
            person.birth = LocalDate.of(1910, Month.FEBRUARY, 1);
            person.status = "alive";

            // persist it
            person.persist();

            List<Person> allPersons = Person.listAll();

            allPersons.forEach(System.out::println);

            long countAll = Person.count();

            System.out.println("Person count:" + countAll);

        }
    }
}

