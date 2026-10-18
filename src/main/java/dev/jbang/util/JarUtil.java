package dev.jbang.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.devkitman.Jdk;

public final class JarUtil {
    private JarUtil() {
    }

    public static void createJar(Path jar, Path src, Manifest manifest, String mainClass, Jdk jdk)
            throws IOException {
        Manifest mf = prepareManifest(manifest, mainClass);

        Util.verboseMsg("Package: " + jar);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            if (src != null) {
                addDirectory(jos, src, src);
            }
        }
    }

    public static void updateJar(Path jar, Manifest manifest, String mainClass, Jdk jdk)
            throws IOException {
        Manifest mf = prepareManifest(manifest, mainClass);

        Util.verboseMsg("Package: " + jar);
        // A jar file cannot be updated in place, so we write a new one next to
        // the original and move it into place when we're done
        Path tmpJar = Files.createTempFile(jar.toAbsolutePath().getParent(), "jbang-jar", ".tmp");
        boolean done = false;
        try {
            try (JarFile jf = new JarFile(jar.toFile());
                 JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tmpJar), mf)) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (JarFile.MANIFEST_NAME.equals(entry.getName())) {
                        // The manifest is written by the JarOutputStream
                        // constructor so we skip the original one
                        continue;
                    }
                    jos.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream is = jf.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = is.read(buffer)) > 0) {
                            jos.write(buffer, 0, n);
                        }
                    }
                    jos.closeEntry();
                }
            }
            Files.move(tmpJar, jar, StandardCopyOption.REPLACE_EXISTING);
            done = true;
        } finally {
            if (!done) {
                Files.deleteIfExists(tmpJar);
            }
        }
    }

    private static Manifest prepareManifest(Manifest manifest, String mainClass) {
        if (manifest == null) {
            manifest = new Manifest();
        }
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        return manifest;
    }

    private static void addDirectory(JarOutputStream jos, Path root, Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> items = paths.sorted().collect(Collectors.toList());
            for (Path item : items) {
                if (item.equals(root)) {
                    continue;
                }
                String name = root.relativize(item).toString().replace(File.separatorChar, '/');
                if (JarFile.MANIFEST_NAME.equals(name)) {
                    // The manifest we create ourselves takes precedence over
                    // any file with that name found in the source directory
                    continue;
                }
                if (Files.isDirectory(item)) {
                    jos.putNextEntry(new JarEntry(name + "/"));
                    jos.closeEntry();
                } else {
                    jos.putNextEntry(new JarEntry(name));
                    Files.copy(item, jos);
                    jos.closeEntry();
                }
            }
        }
    }
}
