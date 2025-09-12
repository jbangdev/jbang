import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class FixCommentSpacing {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: jbang FixCommentSpacing.java <filename>");
            System.exit(1);
        }

        Path path = Paths.get(args[0]);
        List<String> lines = Files.readAllLines(path);

        List<String> fixedLines =
                lines.stream()
                        .map(FixCommentSpacing::fixCommentSpacing)
                        .collect(Collectors.toList());

        Files.write(path, fixedLines);
    }

    private static String fixCommentSpacing(String line) {
        if (line.startsWith("/// ")) {
            return line.replaceFirst("///\\s+", "///");
        } else if (line.startsWith("// ")) {
            return line.replaceFirst("//\\s+", "//");
        } else {
            return line;
        }
    }
}
