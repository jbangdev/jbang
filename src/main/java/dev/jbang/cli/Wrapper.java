package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "wrapper", description = "Manage jbang wrapper for a folder.")
public class Wrapper {
	public static final String DIR_NAME = ".jbang";
	public static final String SHELL_NAME = "jbang";
	public static final String CMD_NAME = "jbang.cmd";
	public static final String JAR_NAME = "jbang.jar";

	@CommandLine.Command(name = "install", description = "Install/Setup jbang as a `wrapper` script in a folder")
	public Integer install(
			@CommandLine.Option(names = { "-d",
					"--dir" }, description = "The folder to install the wrapper into.") Path dest,
			@CommandLine.Option(names = { "-f",
					"--force" }, description = "Force installation of wrapper even if files already exist") boolean force) {
		if (!Files.isDirectory(dest)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Destination folder does not exist");
		}
		if ((checkScripts(dest) || checkJar(dest.resolve(DIR_NAME))) && !force) {
			Util.warnMsg("Wrapper already exists. Use --force to install anyway");
			return EXIT_OK;
		}
		try {
			Path jar = Util.getJarLocation();
			if (!jar.toString().endsWith(".jar")) {
				throw new ExitException(EXIT_GENERIC_ERROR, "Couldn't find JBang install location");
			}
			Path parent = jar.getParent();
			if (checkScripts(parent) && checkJar(parent)) {
				copyScripts(parent, dest);
				copyJar(parent, dest);
			} else if (parent.getFileName().toString().equals(DIR_NAME)
					&& checkScripts(parent.getParent())
					&& checkJar(parent)) {
				copyScripts(parent.getParent(), dest);
				copyJar(parent, dest);
			} else {
				throw new ExitException(EXIT_GENERIC_ERROR, "Couldn't find JBang wrapper files");
			}
			return EXIT_OK;
		} catch (IOException e) {
			throw new ExitException(EXIT_GENERIC_ERROR, "Couldn't copy JBang wrapper scripts", e);
		}
	}

	private boolean checkScripts(Path dir) {
		return Files.isRegularFile(dir.resolve(SHELL_NAME)) && Files.isRegularFile(dir.resolve(CMD_NAME));
	}

	private boolean checkJar(Path dir) {
		return Files.isRegularFile(dir.resolve(JAR_NAME));
	}

	private void copyScripts(Path dir, Path dest) throws IOException {
		Files.copy(dir.resolve(SHELL_NAME), dest.resolve(SHELL_NAME), COPY_ATTRIBUTES, REPLACE_EXISTING);
		Files.copy(dir.resolve(CMD_NAME), dest.resolve(CMD_NAME), COPY_ATTRIBUTES, REPLACE_EXISTING);
	}

	private void copyJar(Path dir, Path dest) throws IOException {
		Path jbdir = dest.resolve(DIR_NAME);
		jbdir.toFile().mkdirs();
		Files.copy(dir.resolve(JAR_NAME), jbdir.resolve(JAR_NAME), COPY_ATTRIBUTES, REPLACE_EXISTING);
	}
}
