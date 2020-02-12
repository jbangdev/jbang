package dk.xam.jbang;

import static dk.xam.jbang.Settings.CP_SEPARATOR;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import org.codehaus.plexus.languages.java.jpms.*;

public class ModularClassPath {

	static final String JAVAFX_PREFIX = "javafx";

	final String classPath;
	boolean javafx = false;

	public ModularClassPath(String classPath) {
		this.classPath = classPath;
		javafx = classPath.contains("org/openjfx/javafx-") || classPath.contains("org\\openjfx\\javafx-");
	}

	String getClassPath() {
		return classPath;
	}

	List<String> getAutoDectectedModuleArguments(String javacmd) {
		if (javafx && supportsModules(javacmd)) {
			List<String> commandArguments = new ArrayList<>();

			List<File> artifacts = Arrays.stream(getClassPath().split(CP_SEPARATOR)).map(it -> new File(it))
					.collect(Collectors.toList());

			ResolvePathsRequest<File> result = ResolvePathsRequest.ofFiles(artifacts)
					.setModuleDescriptor(JavaModuleDescriptor.newModule("bogus").build());

			LocationManager lm = new LocationManager();

			try {
				ResolvePathsResult<File> resolvePathsResult = lm.resolvePaths(result);

				Map<File, ModuleNameSource> modulepathElements = resolvePathsResult.getModulepathElements();
				List<String> modulePaths = new ArrayList<>();
				Map<String, JavaModuleDescriptor> pathElements = new HashMap<>();

				resolvePathsResult.getModulepathElements().keySet()
						.forEach(file -> modulePaths.add(file.getPath()));

				resolvePathsResult.getPathElements().forEach((key, value) -> pathElements.put(key.getPath(), value));

				pathElements.forEach((k, v) -> {
					if (v != null && v.name() != null && v.name().startsWith(JAVAFX_PREFIX)) {
						// only JavaFX jars are required in the module-path
						modulePaths.add(k);
					} else {
						// classpathElements.add(k);
					}
				});

				if (!modulePaths.isEmpty()) {
					commandArguments.add("--module-path");
					String modulePath = String.join(File.pathSeparator, modulePaths);
					commandArguments.add(modulePath);
				}

				String modules = pathElements.values().stream()
						.filter(Objects::nonNull)
						.map(JavaModuleDescriptor::name)
						.filter(Objects::nonNull)
						.filter(module -> module.startsWith(JAVAFX_PREFIX) && !module.endsWith("Empty"))
						.collect(Collectors.joining(","));
				if (!modules.trim().isEmpty()) {
					commandArguments.add("--add-modules");
					commandArguments.add(modules);
				}

			} catch (IOException io) {
				// TODO: warn/log
				return Collections.emptyList();
			}
			return commandArguments;
		} else {
			return Collections.emptyList();
		}
	}

	private boolean supportsModules(String javacmd) {
		ProcessBuilder pb = new ProcessBuilder();
		boolean result;
		try {
			Process p = pb.command(javacmd, "-version").start();
			p.waitFor(); // wait for process to finish then continue.

			try (BufferedReader bri = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
				result = !bri.readLine().contains("\"1."); // anything starting with 1. would be 1.8 and older
			}

		} catch (IOException | InterruptedException e) {
			result = !System.getProperty("java.version").startsWith("1."); // couldn't decide fall back to running vm to
																			// decide
		}
		// Util.info("java modules: " + result);
		return result;
	}
}
