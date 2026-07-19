package dev.jbang.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Locates the files belonging to the JBang installation currently running. */
final class JBangInstallation {
	static final String DIR_NAME = ".jbang";
	static final String JAR_NAME = "jbang.jar";
	static final List<String> SCRIPT_NAMES = Arrays.asList("jbang", "jbang.cmd", "jbang.ps1");

	private final Path scriptsDir;
	private final Path jarDir;

	private JBangInstallation(Path scriptsDir, Path jarDir) {
		this.scriptsDir = scriptsDir;
		this.jarDir = jarDir;
	}

	static JBangInstallation find(Path location) {
		if (!isJBangArtifact(location)) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
					"Could not determine JBang installation from " + location);
		}

		Path parent = location.getParent();
		if (hasScripts(parent) && hasJar(parent)) {
			return new JBangInstallation(parent, parent);
		}
		if (parent != null && DIR_NAME.equals(fileName(parent))) {
			Path scriptsDir = parent.getParent();
			if (hasScripts(scriptsDir) && hasJar(parent)) {
				return new JBangInstallation(scriptsDir, parent);
			}
		}

		throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
				"Could not find JBang installation files relative to " + location);
	}

	Path getScriptsDir() {
		return scriptsDir;
	}

	Path getJarDir() {
		return jarDir;
	}

	private static boolean isJBangArtifact(Path location) {
		if (location == null || location.getFileName() == null) {
			return false;
		}
		String name = location.getFileName().toString();
		return name.endsWith(".jar") || name.contains("jbang.bin");
	}

	private static boolean hasScripts(Path dir) {
		return dir != null && SCRIPT_NAMES.stream().map(dir::resolve).allMatch(Files::isRegularFile);
	}

	private static boolean hasJar(Path dir) {
		return dir != null && Files.isRegularFile(dir.resolve(JAR_NAME));
	}

	private static String fileName(Path path) {
		return path.getFileName() != null ? path.getFileName().toString() : "";
	}
}
