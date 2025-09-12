import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class FixJBangLine1 {
    private static int lnum = 0;
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: jbang FixJBangLine1.java <filename>");
            System.exit(1);
        }

        Path path = Paths.get(args[0]);
        List<String> lines = Files.readAllLines(path);

        List<String> fixedLines =
                lines.stream()
                        .map(FixJBangLine1::fixCommentSpacing)
                        .collect(Collectors.toList());

        Files.write(path, fixedLines);
    }

    private static String fixCommentSpacing(String line) {
        lnum += 1;
        if (lnum == 1 && line.startsWith("/// ") && line.contains("usr/bin/env") && line.contains("jbang")) {
            // System.out.println("[" + line + "]");
            // Output -> [/// usr/bin/env jbang "$0" "$@" ; exit $?]
            return line.replaceFirst("///\\s+", "///");
        } else {
            return line;
        }
    }
}
