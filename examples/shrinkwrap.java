//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-api:3.1.4 org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-impl-maven:3.1.4

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.filter.MavenResolutionFilter;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.MavenResolutionStrategy;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.TransitiveExclusionPolicy;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "shrinkwrap", mixinStandardHelpOptions = true, version = "shrinkwrap 0.1",
        description = "shrinkwrap made with jbang")
class shrinkwrap implements Callable<Integer> {

    @Parameters(index = "0", description = "The greeting to print", defaultValue = "World!")
    private String greeting;

    public static void main(String... args) {
        int exitCode = new CommandLine(new shrinkwrap()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        System.out.println("Hello " + greeting);

        ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
                .withRemoteRepo("jcenter", "https://jcenter.bintray.com/", "default")
                .withMavenCentralRepo(false);
        
        //System.setProperty("maven.repo.local", Settings.getLocalMavenRepo().toPath().toAbsolutePath().toString());

        List<File> artifacts = resolver.resolve("log4j:log4j:1.2.17")
                .using(new MavenResolutionStrategy() {

                    @Override
                    public TransitiveExclusionPolicy getTransitiveExclusionPolicy() {
                        return new TransitiveExclusionPolicy() {
                            @Override
                            public boolean allowOptional() {
                                return true;
                            }

                            @Override
                            public ScopeType[] getFilteredScopes() {
                                return new ScopeType[]{ScopeType.PROVIDED, ScopeType.TEST};
                            }
                        };
                    }

                    @Override
                    public MavenResolutionFilter[] getResolutionFilters() {
                        return new MavenResolutionFilter[0];
                    }
                })
                .asList(File.class);

        artifacts.forEach(System.out::println);

        return CommandLine.ExitCode.OK;
    }
}
