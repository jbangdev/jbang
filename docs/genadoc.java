///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.aesh:aesh:3.19
///COMPILE_OPTIONS -proc:none
//JAVA 17+

import org.aesh.command.ArgumentsSection;
import org.aesh.command.Command;
import org.aesh.command.DocFormat;
import org.aesh.command.HelpFeature;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.util.doc.DocumentationGenerator;

import java.lang.reflect.Field;
import java.util.EnumSet;

import java.io.*;
import java.nio.file.*;

/**
 * Generates AsciiDoc CLI reference pages from aesh command metadata.
 * Delegates to aesh's built-in DocumentationGenerator.
 *
 * Usage: jbang docs/genadoc.java -d docs/modules/cli [--format asciidoc|markdown|skill] --force dev.jbang.cli.JBang
 */
public class genadoc {

    public static void main(String[] args) throws Exception {
        String outDir = ".";
        String crossRefPrefix = "jbang:cli:";
        String className = null;
        DocFormat format = DocFormat.ASCIIDOC;

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
                case "--format":
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --format requires a value (asciidoc, markdown, skill)");
                        System.exit(2);
                    }
                    try {
                        format = DocFormat.valueOf(args[++i].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        System.err.println("Error: unknown format '" + args[i] + "'. Supported: asciidoc, markdown, skill");
                        System.exit(2);
                    }
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
            System.err.println("Usage: genadoc [-d outdir] [--format asciidoc|markdown|skill] [--force] [--component-root prefix] <commandClass>");
            System.exit(2);
        }

        @SuppressWarnings("unchecked")
        Class<? extends Command> cmdClass = (Class<? extends Command>) Class.forName(className);

        Path pagesDir = Paths.get(outDir, "pages");
        Files.createDirectories(pagesDir);

        // Remove previously generated pages before regenerating: the generator
        // refuses to overwrite a file whose content differs (its guard against
        // two commands flattening to the same slug), so leftover pages from an
        // earlier run would otherwise block regeneration. Dropped/renamed
        // commands also leave no stale page behind this way.
        String pageSuffix = "." + format.extension();
        try (java.util.stream.Stream<Path> existing = Files.list(pagesDir)) {
            existing.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("jbang") && n.endsWith(pageSuffix);
            }).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new RuntimeException("Could not delete stale page " + p, e);
                }
            });
        }

        // DocumentationGenerator.Builder has no helpDefaults/argumentsSectionDefault
        // setters, so we build a parser via the registry builder (which does) and
        // feed that in. This keeps the generated docs in sync with terminal
        // `--help` output. Tracked upstream as aesh#776.
        CommandLineParser<?> parser = buildConfiguredParser(cmdClass);

        DocumentationGenerator.Builder builder = DocumentationGenerator.builder()
            .parser(parser)
            .outputDir(pagesDir.toFile())
            .crossRefPrefix(crossRefPrefix)
            .format(format);
        if (format == DocFormat.ASCIIDOC) {
            builder.navFile(Paths.get(outDir, "nav.adoc").toFile());
        }
        builder.generate();

        System.out.println("Documentation generated in " + outDir);
    }

    /**
     * Builds a CommandLineParser with the same fleet-wide help toggles jbang applies
     * at runtime (HELP_DEFAULTS, ARGUMENTS_SECTION). Those live as public static
     * fields on the root command class; if the target class doesn't expose them,
     * aesh defaults are used.
     */
    @SuppressWarnings("unchecked")
    private static CommandLineParser<?> buildConfiguredParser(Class<? extends Command> cmdClass) throws Exception {
        EnumSet<HelpFeature> helpDefaults = readStaticField(cmdClass, "HELP_DEFAULTS", EnumSet.class);
        ArgumentsSection argumentsSection = readStaticField(cmdClass, "ARGUMENTS_SECTION", ArgumentsSection.class);

        AeshCommandRegistryBuilder<?> regBuilder = AeshCommandRegistryBuilder.builder();
        if (helpDefaults != null) {
            regBuilder.helpDefaults(helpDefaults);
        }
        if (argumentsSection != null) {
            regBuilder.argumentsSectionDefault(argumentsSection);
        }
        CommandRegistry<?> registry = regBuilder.command(cmdClass).create();
        String rootName = (String) registry.getAllCommandNames().iterator().next();
        return registry.getCommand(rootName, rootName).getParser();
    }

    private static <T> T readStaticField(Class<?> cls, String name, Class<T> expected) {
        try {
            Field f = cls.getField(name);
            Object v = f.get(null);
            if (v != null && expected.isInstance(v)) {
                return expected.cast(v);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // optional field; fall back to aesh defaults
        }
        return null;
    }
}
