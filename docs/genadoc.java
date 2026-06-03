///usr/bin/env jbang "$0" "$@" ; exit $?
// aesh classes are provided via --cp from the jbang shadow jar
///COMPILE_OPTIONS -proc:none
//JAVA 17+

import org.aesh.command.Command;
import org.aesh.util.doc.DocFormat;
import org.aesh.util.doc.DocumentationGenerator;

import java.io.*;
import java.nio.file.*;

/**
 * Generates AsciiDoc CLI reference pages from aesh command metadata.
 * Delegates to aesh's built-in DocumentationGenerator.
 *
 * Usage: jbang docs/genadoc.java -d docs/modules/cli --force dev.jbang.cli.JBang
 */
public class genadoc {

    public static void main(String[] args) throws Exception {
        String outDir = ".";
        String crossRefPrefix = "jbang:cli:";
        String className = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                case "--outdir":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: " + args[i] + " requires a value");
                        System.exit(2);
                    }
                    outDir = args[++i];
                    break;
                case "--force":
                case "-f":
                    // force is always on with the new generator (overwrites existing files)
                    break;
                case "--component-root":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --component-root requires a value");
                        System.exit(2);
                    }
                    crossRefPrefix = args[++i];
                    break;
                default:
                    className = args[i];
            }
        }

        if (className == null) {
            System.err.println("Usage: genadoc [-d outdir] [--force] [--component-root prefix] <commandClass>");
            System.exit(2);
        }

        @SuppressWarnings("unchecked")
        Class<? extends Command> cmdClass = (Class<? extends Command>) Class.forName(className);

        Path pagesDir = Paths.get(outDir, "pages");
        Files.createDirectories(pagesDir);

        DocumentationGenerator.builder()
            .commandClass(cmdClass)
            .outputDir(pagesDir.toFile())
            .crossRefPrefix(crossRefPrefix)
            .navFile(Paths.get(outDir, "nav.adoc").toFile())
            .format(DocFormat.ASCIIDOC)
            .generate();

        System.out.println("Documentation generated in " + outDir);
    }
}
