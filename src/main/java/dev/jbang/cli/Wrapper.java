package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_OK;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.ExitException;
import dev.jbang.Util;

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
					"--dir" }, description = "The folder to install the wrapper into.", defaultValue = ".") Path dest,
			@CommandLine.Option(names = { "-f",
					"--force" }, description = "Force installation of wrapper even if files already exist", defaultValue = "false") boolean force) {
		if (!Files.isDirectory(dest)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Destination folder does not exist");
		}
		if ((checkScripts(dest) || checkJar(dest.resolve(DIR_NAME))) && !force) {
			Util.warnMsg("Wrapper already exists. Use --force to install anyway");
			return EXIT_OK;
		}
		try {
			URI uri = Wrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path jar = new File(uri).toPath();
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
				throw new ExitException(1, "Couldn't find Jbang wrapper files");
			}
			return EXIT_OK;
		} catch (URISyntaxException e) {
			throw new ExitException(1, "Couldn't find Jbang install location", e);
		} catch (IOException e) {
			throw new ExitException(1, "Couldn't copy Jbang wrapper scripts", e);
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
