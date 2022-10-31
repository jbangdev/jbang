package dev.jbang.net.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class ScoopJdkProvider extends BaseFoldersJdkProvider {
	private static final Path SCOOP_APPS = Paths.get(System.getProperty("user.home")).resolve("scoop/apps");

	public static boolean canUse() {
		return Util.isWindows() && Files.isDirectory(SCOOP_APPS);
	}

	@Nullable
	@Override
	public Jdk getDefault() {
		return null;
	}

	@Nonnull
	@Override
	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(getJdksRoot())) {
			return Files.list(getJdksRoot())
						.filter(p -> p.getFileName().startsWith("openjdk"))
						.map(p -> p.resolve("current"));
		}
		return Stream.empty();
	}

	@Nullable
	@Override
	protected Path getDefaultJdkPath() {
		return null;
	}

	@Override
	protected String jdkId(String name) {
		return name + "-scoop";
	}

	@Nullable
	@Override
	protected Jdk createJdk(Path home) {
		try {
			// Try to resolve any links
			home = home.toRealPath();
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Couldn't resolve 'current' link: " + home, e);
		}
		return super.createJdk(home);
	}
}
