package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.*;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

import dev.jbang.ExitException;
import dev.jbang.util.Util;

@CommandDefinition(name = "wrapper", description = "Manage jbang wrapper for a folder.", groupCommands = {
		Wrapper.WrapperInstall.class })
public class Wrapper extends BaseCommand {
	public static final String DIR_NAME = ".jbang";
	public static final String JAR_NAME = "jbang.jar";
	public static final List<String> SCRIPT_NAMES = Arrays.asList("jbang", "jbang.cmd", "jbang.ps1");

	@Override
	public Integer doCall() {
		return missingSubcommand();
	}

	@Override
	public String prose() {
		return "Adds jbang wrapper scripts to a folder (usually a project), so anyone can run\n"
				+ "your scripts with `./jbang` without installing jbang first.";
	}

	@Override
	public List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} install",
				"${COMMAND-FULL-NAME} install -d myproject");
	}

	@CommandDefinition(name = "install", description = "Install/Setup jbang as a wrapper script in a folder")
	public static class WrapperInstall extends BaseCommand {

		@Option(shortName = 'd', name = "dir", description = "The folder to install the wrapper into.")
		Path dest;

		@Option(shortName = 'f', name = "force", hasValue = false, description = "Force installation of wrapper even if files already exist")
		boolean force;

		@Override
		public Integer doCall() {
			Path destPath = dest != null ? dest : Paths.get(".");
			if (!Files.isDirectory(destPath)) {
				throw new ExitException(ExitException.EXIT_INVALID_INPUT, "Destination folder does not exist");
			}
			if ((checkScripts(destPath) || checkJar(destPath.resolve(DIR_NAME))) && !force) {
				Util.warnMsg("Wrapper already exists. Use --force to install anyway");
				return ExitException.EXIT_OK;
			}
			try {
				Path jar = Util.getJarLocation();
				String jarName = jar.getFileName().toString();
				if (!jarName.endsWith(".jar") && !jarName.contains("jbang.bin")) {
					throw new ExitException(ExitException.EXIT_GENERIC_ERROR,
							"Couldn't find JBang install location via " + jar);
				}
				Path parent = jar.getParent();
				if (checkScripts(parent) && checkJar(parent)) {
					copyScripts(parent, destPath);
					copyJar(parent, destPath);
				} else if (parent.getFileName().toString().equals(DIR_NAME)
						&& checkScripts(parent.getParent())
						&& checkJar(parent)) {
					copyScripts(parent.getParent(), destPath);
					copyJar(parent, destPath);
				} else {
					throw new ExitException(ExitException.EXIT_GENERIC_ERROR, "Couldn't find JBang wrapper files");
				}
				return ExitException.EXIT_OK;
			} catch (IOException e) {
				throw new ExitException(ExitException.EXIT_GENERIC_ERROR, "Couldn't copy JBang wrapper scripts", e);
			}
		}

		private boolean checkScripts(Path dir) {
			return SCRIPT_NAMES.stream().map(dir::resolve).allMatch(Files::isRegularFile);
		}

		private boolean checkJar(Path dir) {
			return Files.isRegularFile(dir.resolve(JAR_NAME));
		}

		private void copyScripts(Path dir, Path dest) throws IOException {
			for (String nm : SCRIPT_NAMES) {
				Files.copy(dir.resolve(nm), dest.resolve(nm), COPY_ATTRIBUTES, REPLACE_EXISTING);
				touch(dest.resolve(nm));
			}
		}

		private void copyJar(Path dir, Path dest) throws IOException {
			Path jbdir = dest.resolve(DIR_NAME);
			jbdir.toFile().mkdirs();
			Files.copy(dir.resolve(JAR_NAME), jbdir.resolve(JAR_NAME), COPY_ATTRIBUTES, REPLACE_EXISTING);
			touch(jbdir.resolve(JAR_NAME));
		}

		/**
		 * Sets the modification time to now. Release archives are built with fixed 1980
		 * timestamps (reproducible builds) and COPY_ATTRIBUTES would carry those over.
		 * Best-effort, like COPY_ATTRIBUTES itself: a filesystem that doesn't allow
		 * setting it must not make the install fail.
		 */
		private static void touch(Path file) {
			try {
				Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
			} catch (IOException e) {
				Util.verboseMsg("Could not update modification time of " + file + ": " + e.getMessage());
			}
		}

		@Override
		public String prose() {
			return "Copies the jbang wrapper (jbang, jbang.cmd, jbang.ps1 and .jbang/jbang.jar) into\n"
					+ "the current folder, or the one given with --dir. Commit them so others can use\n"
					+ "`./jbang`.";
		}

		@Override
		public List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} -d myproject",
					"${COMMAND-FULL-NAME} --force");
		}
	}
}
