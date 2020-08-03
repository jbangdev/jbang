//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Command(name = "findmodule", mixinStandardHelpOptions = true, version = "findmodule 0.1",
        description = "findmodule made with jbang")
class findmodule implements Callable<Integer> {

    @CommandLine.Option(names="--javahome", defaultValue = "${JAVA_HOME}", required = true)
    private Path javahome;

    @Parameters(index = "0")
    String pattern;

    public static void main(String... args) {
        int exitCode = new CommandLine(new findmodule()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
    //    System.out.println("Hello " + greeting);

        var mods = javahome.resolve("jmods").toFile();

        if(mods.exists() && mods.isDirectory()) {

            File[] files = javahome.resolve("jmods").toFile().listFiles((dir, name) -> {
                return name.endsWith(".jmod");
            });

            for (File f: files) {
                try(ZipFile zipFile = new ZipFile(f)) {
                    zipFile.stream().map(ZipEntry::getName).filter(x -> x.contains(pattern)).forEach(
                            i -> { System.out.println(f + " -> " + i); });
                }

            }

        } else {
            System.err.println("Could not find folder named: " + mods.toString());
        }
        return 0;
    }
}
