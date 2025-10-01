
//FILES META-INF/jbang-integration.list=jbang-integration.list

import java.nio.file.Path;
import java.util.*;

public class integration {
    /**
     *
     * @param temporaryJar temporary JAR file path
     * @param pomFile location of pom.xml representing the projects dependencies
     * @param repositories list of the used repositories
     * @param dependencies list of GAV to Path of artifact/classpath dependencies
     * @param comments comments from the source file
     * @param nativeImage true if --native been requested
     * @return Map<String, Object> map of returns; special keys are "native-image" which is a and "files" to
     *          return native-image to be run and list of files to get written to the output directory.
     *
     */
    public static Map<String, Object> postBuild(
            Path temporaryJar,
            Path pomFile,
            List<Map.Entry<String, String>> repositories,
            List<Map.Entry<String, Path>> dependencies,
            List<String> comments,
    boolean nativeImage) {
        System.err.println("Integration called!");
        System.err.println("TMPJAR: " + temporaryJar);
        System.err.println("POM: " + pomFile);
        for (Map.Entry<String, String> r : repositories) {
            System.err.println("REPO: " + r.getKey() + " -> " + r.getValue());
        }
        for (Map.Entry<String, Path> d : dependencies) {
            System.err.println("DEP: " + d.getKey() + " -> " + d.getValue());
        }
        for (String c : comments) {
            System.err.println("COMMENT: " + c);
        }

        String myTest = comments.stream()
                .filter(s -> s.startsWith("//MY:TEST "))
                .findFirst()
                .map(s -> s.substring("//MY:TEST ".length()))
                .orElse("default");
        Map<String, Object> result = new HashMap<>();
        result.put("java-args", Arrays.asList("-Dbar=" + myTest));
        result.put("main-class", "altmain");
        return result;
    }

}