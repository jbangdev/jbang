import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IntegrationTest {
    public static Map<String, Object> postBuild(Path temporaryJar,
                                                Path pomFile,
                                                List<Map.Entry<String, String>> repositories,
                                                List<Map.Entry<String, Path>> dependencies,
                                                List<String> comments,
                                                boolean nativeImage) {
        System.out.println("Integration... (out)");
        System.err.println("Integration... (err)");
        if (System.getProperty("failintegration") != null) {
            throw new RuntimeException("Failing integration...");
        } else {
            System.out.println("Integration OK (out)");
            System.err.println("Integration OK (err)");
            return Collections.emptyMap();
        }
    }
}
