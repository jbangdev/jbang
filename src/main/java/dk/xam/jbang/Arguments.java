package dk.xam.jbang;

import picocli.CommandLine;

import java.util.List;

import static picocli.CommandLine.*;

class Arguments {
    @Option(names={"-d","--debug"}, arity = "0", description = "Launch with java debug enabled.")
    boolean debug;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display help/info")
    boolean helpRequested;

    @Option(names = {"--version"}, versionHelp = true, arity = "0", description = "Display version info")
    boolean versionRequested;

    @Parameters
    List<String> scripts;
}
