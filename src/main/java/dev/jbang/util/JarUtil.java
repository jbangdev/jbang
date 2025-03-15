package dev.jbang.util;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;

import dev.jbang.cli.ExitException;
import dev.jbang.devkitman.Jdk;

public final class JarUtil {
	private JarUtil() {
	}

	public static void createJar(Path jar, Path src, Manifest manifest, String mainClass, Jdk jdk)
			throws IOException {
		runJarCommand(jar, "c", src, manifest, mainClass, jdk);
	}

	public static void updateJar(Path jar, Manifest manifest, String mainClass, Jdk jdk)
			throws IOException {
		runJarCommand(jar, "u", null, manifest, mainClass, jdk);
	}

	private static void runJarCommand(Path jar, String action, Path src, Manifest manifest, String mainClass, Jdk jdk)
			throws IOException {
		assert (action.equals("c") || action.equals("u"));
		List<String> optionList = new ArrayList<>();
		Path tmpManifest = null;
		try {
			action += "f";
			optionList.add(jar.toString());

			if (manifest != null) {
				tmpManifest = Files.createTempFile("jbang-manifest", "mf");
				try (OutputStream out = Files.newOutputStream(tmpManifest)) {
					manifest.write(out);
				}
				action += "m";
				optionList.add(tmpManifest.toString());
			}

			if (mainClass != null) {
				action += "e";
				optionList.add(mainClass);
			}

			optionList.add(0, action);

			if (src != null) {
				optionList.add("-C");
				optionList.add(src.toAbsolutePath().toString());
				optionList.add(".");
			}
			runJarCommand(optionList, jdk);
		} finally {
			if (tmpManifest != null) {
				Util.deletePath(tmpManifest, true);
			}
		}
	}

	private static void runJarCommand(List<String> arguments, Jdk jdk) throws IOException {
		arguments.add(0, resolveInJavaHome("jar", jdk));
		Util.verboseMsg("Package: " + String.join(" ", arguments));
		String out = Util.runCommand(arguments.toArray(new String[] {}));
		if (out == null) {
			throw new ExitException(1, "Error creating/updating jar");
		}
	}
}
