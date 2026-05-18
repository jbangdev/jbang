///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.aesh:aesh:3.7
//DEPS org.aesh:readline-api:3.7
///COMPILE_OPTIONS -proc:none
//JAVA 17+

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.parser.AeshCommandLineParser;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.container.CommandContainer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static java.lang.String.format;

/**
 * Generates AsciiDoc man pages from aesh command metadata.
 * Produces the same output format as the previous picocli-based generator.
 *
 * Usage: jbang docs/genadoc.java -d docs/modules/cli --force dev.jbang.cli.JBang
 */
public class genadoc {

    static String componentRoot = "jbang:cli:";

    public static void main(String[] args) throws Exception {
        String outDir = ".";
        boolean force = false;
        String className = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                case "--outdir":
                    outDir = args[++i];
                    break;
                case "--force":
                case "-f":
                    force = true;
                    break;
                case "--component-root":
                    componentRoot = args[++i];
                    break;
                default:
                    className = args[i];
            }
        }

        if (className == null) {
            System.err.println("Usage: genadoc [-d outdir] [--force] <commandClass>");
            System.exit(2);
        }

        @SuppressWarnings("unchecked")
        Class<? extends Command> cmdClass = (Class<? extends Command>) Class.forName(className);

        AeshCommandContainerBuilder<?> builder = new AeshCommandContainerBuilder<>();
        CommandContainer<?> container = builder.create(cmdClass);
        CommandLineParser<?> rootParser = container.getParser();

        Path pagesDir = Paths.get(outDir, "pages");
        Path partialsDir = Paths.get(outDir, "partials");
        Files.createDirectories(pagesDir);
        Files.createDirectories(partialsDir);

        generateManPages(rootParser, pagesDir);
        generateNav(rootParser, Paths.get(outDir, "nav.adoc"));

        System.out.println("Documentation generated in " + outDir);
    }

    static void generateManPages(CommandLineParser<?> parser, Path pagesDir) throws IOException {
        String rootName = parser.getProcessedCommand().name();
        generateSinglePage(parser, rootName, null, pagesDir);

        List<CommandLineParser<?>> children = getAllChildParsers(parser);
        if (children != null) {
            for (CommandLineParser<?> child : children) {
                String childQualified = rootName + "-" + child.getProcessedCommand().name();
                generateSinglePage(child, childQualified, parser, pagesDir);
                List<CommandLineParser<?>> grandchildren = getAllChildParsers(child);
                if (grandchildren != null) {
                    for (CommandLineParser<?> grandchild : grandchildren) {
                        String gcQualified = childQualified + "-" + grandchild.getProcessedCommand().name();
                        generateSinglePage(grandchild, gcQualified, child, pagesDir);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static List<CommandLineParser<?>> getAllChildParsers(CommandLineParser<?> parser) {
        if (!parser.isGroupCommand()) return null;
        if (parser instanceof AeshCommandLineParser) {
            AeshCommandLineParser<?> aeshParser = (AeshCommandLineParser<?>) parser;
            return (List<CommandLineParser<?>>) (List<?>) aeshParser.getAllChildParsers();
        }
        return null;
    }

    static void generateSinglePage(CommandLineParser<?> parser, String qualifiedName,
            CommandLineParser<?> parent, Path pagesDir) throws IOException {
        Path file = pagesDir.resolve(qualifiedName + ".adoc");

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file.toFile()), "UTF-8"))) {
            writePage(pw, parser, parent, qualifiedName);
        }
    }

    static void writePage(PrintWriter pw, CommandLineParser<?> parser, CommandLineParser<?> parent,
            String qualifiedName) {
        ProcessedCommand<?, ?> cmd = parser.getProcessedCommand();
        String name = cmd.name();
        String description = cmd.description() != null ? cmd.description() : "";
        String appName = parent != null ? parent.getProcessedCommand().name() : name;

        pw.println("// This is a generated documentation file based on aesh");
        pw.println("// To change it update the aesh code or the generator");
        pw.println("// tag::picocli-generated-full-manpage[]");

        // Header
        pw.println("// tag::picocli-generated-man-section-header[]");
        pw.println(":doctype: manpage");
        pw.println(format(":manmanual: %s Manual", appName));
        pw.println(":man-linkstyle: pass:[blue R < >]");
        pw.println(format("= %s(1)", qualifiedName));
        pw.println();
        pw.println("// end::picocli-generated-man-section-header[]");
        pw.println();

        // Name
        pw.println("// tag::picocli-generated-man-section-name[]");
        pw.println("== Name");
        pw.println();
        String displayName = parent != null ? parent.getProcessedCommand().name() + "-" + name : name;
        pw.println(format("%s - %s", displayName, description));
        pw.println();
        pw.println("// end::picocli-generated-man-section-name[]");
        pw.println();

        // Synopsis
        pw.println("// tag::picocli-generated-man-section-synopsis[]");
        pw.println("== Synopsis");
        pw.println();
        pw.print(generateSynopsis(parser, parent));
        pw.println();
        pw.println("// end::picocli-generated-man-section-synopsis[]");
        pw.println();

        // Description
        pw.println("// tag::picocli-generated-man-section-description[]");
        pw.println("== Description");
        pw.println();
        pw.println(description);
        pw.println();
        pw.println("// end::picocli-generated-man-section-description[]");
        pw.println();

        // Options
        pw.println("// tag::picocli-generated-man-section-options[]");
        List<ProcessedOption> options = cmd.getOptions();
        if (options != null && !options.isEmpty()) {
            pw.println("== Options");
            pw.println();
            for (ProcessedOption opt : sorted(options)) {
                if (isHidden(opt)) continue;
                writeOption(pw, opt);
            }
        }
        pw.println("// end::picocli-generated-man-section-options[]");
        pw.println();

        // Arguments
        pw.println("// tag::picocli-generated-man-section-arguments[]");
        ProcessedOption argument = cmd.getArgument();
        ProcessedOption arguments = cmd.getArguments();
        if (argument != null || arguments != null) {
            pw.println("== Arguments");
            pw.println();
            if (argument != null) {
                writeArgument(pw, argument);
            }
            if (arguments != null) {
                writeArgument(pw, arguments);
            }
        }
        pw.println("// end::picocli-generated-man-section-arguments[]");
        pw.println();

        // Subcommands
        pw.println("// tag::picocli-generated-man-section-commands[]");
        List<CommandLineParser<?>> children = getAllChildParsers(parser);
        if (children != null && !children.isEmpty()) {
            pw.println("== Commands");
            pw.println();
            for (CommandLineParser<?> child : children) {
                ProcessedCommand<?, ?> childCmd = child.getProcessedCommand();
                String childQualified = qualifiedName + "-" + childCmd.name();
                pw.println(format("xref:%s%s.adoc[*%s*]::", componentRoot, childQualified, childCmd.name()));
                pw.println(format("  %s", childCmd.description() != null ? childCmd.description() : ""));
                pw.println();
            }
        }
        pw.println("// end::picocli-generated-man-section-commands[]");
        pw.println();

        // Exit status (empty for now)
        pw.println("// tag::picocli-generated-man-section-exit-status[]");
        pw.println("// end::picocli-generated-man-section-exit-status[]");
        pw.println();

        // Footer (empty for now)
        pw.println("// tag::picocli-generated-man-section-footer[]");
        pw.println("// end::picocli-generated-man-section-footer[]");
        pw.println();

        pw.println("// end::picocli-generated-full-manpage[]");
    }

    static String generateSynopsis(CommandLineParser<?> parser, CommandLineParser<?> parent) {
        ProcessedCommand<?, ?> cmd = parser.getProcessedCommand();
        StringBuilder sb = new StringBuilder();
        if (parent != null) {
            sb.append(format("*%s %s*", parent.getProcessedCommand().name(), cmd.name()));
        } else {
            sb.append(format("*%s*", cmd.name()));
        }

        List<ProcessedOption> options = cmd.getOptions();
        if (options != null) {
            for (ProcessedOption opt : sorted(options)) {
                if (isHidden(opt)) continue;
                sb.append(" ");
                if (!opt.isRequired()) sb.append("[");
                if (opt.shortName() != null && !opt.shortName().isEmpty()) {
                    sb.append(format("*-%s*", opt.shortName()));
                } else {
                    sb.append(format("*--%s*", opt.name()));
                }
                if (opt.hasValue()) {
                    sb.append(format("=_%s_", opt.getArgument() != null ? opt.getArgument() : opt.name()));
                }
                if (!opt.isRequired()) sb.append("]");
            }
        }

        ProcessedOption argument = cmd.getArgument();
        if (argument != null) {
            sb.append(format(" [_%s_]", argument.description() != null && !argument.description().isEmpty()
                    ? argument.name() : argument.name()));
        }
        ProcessedOption arguments = cmd.getArguments();
        if (arguments != null) {
            sb.append(format(" [_%s_...]", arguments.name() != null ? arguments.name() : "args"));
        }

        sb.append("\n");
        return sb.toString();
    }

    static void writeOption(PrintWriter pw, ProcessedOption opt) {
        StringBuilder names = new StringBuilder();
        if (opt.shortName() != null && !opt.shortName().isEmpty()) {
            names.append(format("*-%s*", opt.shortName()));
            if (opt.name() != null && !opt.name().isEmpty()) {
                names.append(format(", *--%s*", opt.name()));
            }
        } else if (opt.name() != null && !opt.name().isEmpty()) {
            names.append(format("*--%s*", opt.name()));
        }

        if (opt.hasValue()) {
            names.append(format("=_%s_", opt.getArgument() != null ? opt.getArgument() : opt.name()));
        }

        pw.println(format("%s::", names));
        String desc = opt.description() != null ? opt.description() : "";
        pw.println(format("  %s", desc));
        pw.println();
    }

    static void writeArgument(PrintWriter pw, ProcessedOption arg) {
        String label = arg.name() != null && !arg.name().isEmpty() ? arg.name() : "arg";
        pw.println(format("_%s_::", label));
        String desc = arg.description() != null ? arg.description() : "";
        pw.println(format("  %s", desc));
        pw.println();
    }

    static boolean isHidden(ProcessedOption opt) {
        return opt.getVisibility() == org.aesh.command.option.OptionVisibility.HIDDEN;
    }

    static List<ProcessedOption> sorted(List<ProcessedOption> options) {
        List<ProcessedOption> sorted = new ArrayList<>(options);
        sorted.sort((a, b) -> {
            String na = a.name() != null ? a.name() : (a.shortName() != null ? a.shortName() : "");
            String nb = b.name() != null ? b.name() : (b.shortName() != null ? b.shortName() : "");
            return na.compareToIgnoreCase(nb);
        });
        return sorted;
    }

    static String qualifiedName(CommandLineParser<?> parser, CommandLineParser<?> parent) {
        String name = parser.getProcessedCommand().name();
        if (parent != null) {
            String parentName = parent.getProcessedCommand().name();
            return parentName + "-" + name;
        }
        return name;
    }

    static void generateNav(CommandLineParser<?> rootParser, Path navFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(navFile.toFile()), "UTF-8"))) {
            String rootName = rootParser.getProcessedCommand().name();
            pw.println(format("* xref:%s%s.adoc[%s]", componentRoot, rootName, rootName));

            List<CommandLineParser<?>> children = getAllChildParsers(rootParser);
            if (children != null) {
                for (CommandLineParser<?> child : children) {
                    String childName = child.getProcessedCommand().name();
                    String qualified = rootName + "-" + childName;
                    pw.println(format("** xref:%s%s.adoc[%s]", componentRoot, qualified, childName));

                    List<CommandLineParser<?>> grandchildren = getAllChildParsers(child);
                    if (grandchildren != null) {
                        for (CommandLineParser<?> grandchild : grandchildren) {
                            String gcName = grandchild.getProcessedCommand().name();
                            String gcQualified = qualified + "-" + gcName;
                            pw.println(format("*** xref:%s%s.adoc[%s]", componentRoot, gcQualified, gcName));
                        }
                    }
                }
            }
        }
    }
}
