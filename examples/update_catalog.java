//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.5.0
//DEPS com.google.code.gson:gson:2.8.6

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/*
 * This script takes the names of the files that are passed to it and
 * adds them as aliases to the specified Jbang Catalog file.
 */
@Command(name = "update_catalog",
    mixinStandardHelpOptions = true,
    description = "Updates or creates a JBang catalog file")
public class update_catalog implements Callable<Integer> {
    @Option(names = { "--output", "-o" }, description = "Output file", defaultValue = "jbang-catalog.json")
    Path output;
    
    @Option(names = { "--base", "-b" }, description = "Base path")
    Path base;
    
    @Option(names = { "--description", "-d" }, description = "Catalog description")
    String description;
    
    @Parameters(description = "Files to include in the catalog", arity = "1..*")
    protected Path[] files;

    @Override
    public Integer call() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Aliases aliases;
        if (Files.isRegularFile(output)) {
            try (Reader in = Files.newBufferedReader(output)) {
                aliases = gson.fromJson(in, Aliases.class);
            }
        } else {
            aliases = new Aliases();
        }

        System.out.println("OUTPUT: " + output);

        if (base != null) {
            if (aliases.baseRef != null && !aliases.baseRef.equals(base.toString())) {
                throw new RuntimeException("Base path specified on the command line does not match the one in the existing catalog file. There can only be one base path!");
            }
            aliases.baseRef = base.toString();
        } else {
            if (aliases.baseRef != null) {
                base = Paths.get(aliases.baseRef);
            }
        }
        System.out.println("BASE: " + (base != null ? base : "."));

        if (description != null) {
            aliases.description = description;
            System.out.println("DESCRIPTION: " + description);
        }

        for (Path f : files) {
            if (base != null && f.startsWith(base)) {
                f = base.relativize(f);
            }
            String name = f.getFileName().toString();
            int p = name.lastIndexOf(".");
            if (p > 0) {
                name = name.substring(0, p);
            }
            if (!aliases.aliases.containsKey(name)) {
                Alias alias = new Alias(f.toString(), null, null, null);            
                aliases.aliases.put(name, alias);
                System.out.println("Adding file " + f + "' as '" + name + "'");
            }
        }

        try (Writer out = Files.newBufferedWriter(output)) {
            gson.toJson(aliases, out);
        }
        
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new update_catalog()).execute(args);
        System.exit(exitCode);
    }

	public static class Alias {
        @SerializedName(value = "script-ref", alternate = { "scriptRef" })
		public final String scriptRef;
		public final String description;
		public final List<String> arguments;
		public final Map<String, String> properties;

		public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties) {
			this.scriptRef = scriptRef;
			this.description = description;
			this.arguments = arguments;
			this.properties = properties;
		}
	}

	public static class Aliases {
		public Map<String, Alias> aliases = new HashMap<>();
        @SerializedName(value = "base-ref", alternate = { "baseRef" })
		public String baseRef;
		public String description;
	}
}
