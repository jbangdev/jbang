//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.asciidoctor:asciidoctorj:2.2.0

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.jruby.AsciiDocDirectoryWalker;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Callable;

@Command(name = "asciidoc", mixinStandardHelpOptions = true, version = "asciidoc 0.1",
        description = "prints table of contents for a asciidoc")
class asciidoc implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory", arity = "1")
    private File file;

    public static void main(String... args) {
        int exitCode = new CommandLine(new asciidoc()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {

        OptionsBuilder options = OptionsBuilder.options();
        Asciidoctor asciidoctor = Asciidoctor.Factory.create();

        options.sourcemap(true);

        Iterable<File> files;

        if(file.isDirectory()) {
            files = new AsciiDocDirectoryWalker(file.getPath());
        } else {
            files = Arrays.asList(file);
        }
        for(File file : files) {
            var doc = asciidoctor.loadFile(file, options.asMap());
            printToc("", doc);

        }
        return 0;
    }

    private void printToc(String indent, StructuralNode doc) {
        if(doc.getTitle()!=null) {
            System.out.println(indent + doc.getTitle());
        }
        doc.getBlocks().forEach(b -> { printToc(indent + " ", b); });
    }
}
