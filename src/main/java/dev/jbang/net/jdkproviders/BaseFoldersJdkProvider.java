package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.parseJavaOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class BaseFoldersJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public List<Jdk> listInstalled() {
		if (Files.isDirectory(getJdksRoot())) {
			try (Stream<Path> jdkPaths = listJdkPaths()) {
				return jdkPaths
								.map(this::createJdk)
								.filter(Objects::nonNull)
								.sorted(Jdk::compareTo)
								.collect(Collectors.toList());
			} catch (IOException e) {
				Util.verboseMsg("Couldn't list installed JDKs", e);
			}
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public Jdk getJdkByPath(@Nonnull Path jdkPath) {
		if (jdkPath.startsWith(getJdksRoot())) {
			try (Stream<Path> jdkPaths = listJdkPaths()) {
				return jdkPaths
								.filter(jdkPath::startsWith)
								.map(this::createJdk)
								.filter(Objects::nonNull)
								.findFirst()
								.orElse(null);
			} catch (IOException e) {
				Util.verboseMsg("Couldn't list installed JDKs", e);
			}
		}
		return null;
	}

	/**
	 * Returns a path to the requested JDK. This method should never return
	 * <code>null</code> and should return the path where the requested JDK is
	 * either currently installed or where it would be installed if it were
	 * available. This only needs to be implemented for providers that are
	 * updatable.
	 *
	 * @param jdk The identifier of the JDK to install
	 * @return A path to the requested JDK
	 */
	@Nonnull
	protected Path getJdkPath(@Nonnull String jdk) {
		return getJdksRoot().resolve(jdk);
	}

	private Predicate<Path> sameJdk(Path jdkRoot) {
		Path release = jdkRoot.resolve("release");
		return (Path p) -> {
			try {
				return Files.isSameFile(p.resolve("release"), release);
			} catch (IOException e) {
				return false;
			}
		};
	}

	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(getJdksRoot())) {
			return Files.list(getJdksRoot());
		}
		return Stream.empty();
	}

	@Nonnull
	protected Path getJdksRoot() {
		throw new UnsupportedOperationException("Getting the JDK root folder not supported by " + getClass().getName());
	}

	@Nullable
	protected Jdk createJdk(Path home) {
		String name = home.getFileName().toString();
		Optional<String> version = resolveJavaVersionStringFromPath(home);
		if (version.isPresent()) {
			return createJdk(jdkId(name), home, version.get());
		}
		return null;
	}

	protected abstract String jdkId(String name);

	public static Optional<Integer> resolveJavaVersionFromPath(Path home) {
		return resolveJavaVersionStringFromPath(home).map(JavaUtil::parseJavaVersion);
	}

	public static Optional<String> resolveJavaVersionStringFromPath(Path home) {
		Optional<String> res = readJavaVersionStringFromReleaseFile(home);
		if (!res.isPresent()) {
			res = readJavaVersionStringFromJavaCommand(home);
		}
		return res;
	}

	private static Optional<String> readJavaVersionStringFromReleaseFile(Path home) {
		try (Stream<String> lines = Files.lines(home.resolve("release"))) {
			return lines
						.filter(l -> l.startsWith("JAVA_VERSION"))
						.map(JavaUtil::parseJavaOutput)
						.findAny();
		} catch (IOException e) {
			Util.verboseMsg("Unable to read 'release' file in path: " + home);
			return Optional.empty();
		}
	}

	private static Optional<String> readJavaVersionStringFromJavaCommand(Path home) {
		Optional<String> res;
		Path javaCmd = Util.searchPath("java", home.resolve("bin").toString());
		if (javaCmd != null) {
			String output = Util.runCommand(javaCmd.toString(), "-version");
			res = Optional.ofNullable(parseJavaOutput(output));
		} else {
			res = Optional.empty();
		}
		if (!res.isPresent()) {
			Util.verboseMsg("Unable to obtain version from: '" + javaCmd + " -version'");
		}
		return res;
	}
}
