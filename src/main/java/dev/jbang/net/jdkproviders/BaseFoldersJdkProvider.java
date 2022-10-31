package dev.jbang.net.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
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
	public SortedSet<Jdk> listInstalled() {
		if (Files.isDirectory(getJdksRoot())) {
			try (Stream<Path> jdkPaths = listJdkPaths()) {
				return jdkPaths
								.map(this::createJdk)
								.filter(Objects::nonNull)
								.collect(Collectors.toCollection(TreeSet::new));
			} catch (IOException e) {
				Util.verboseMsg("Couldn't list installed JDKs", e);
			}
		}
		return Collections.emptySortedSet();
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

	@Nullable
	@Override
	public Jdk getDefault() {
		try {
			Path link = getDefaultJdkPath();
			if (link != null && Files.isDirectory(link)) {
				if (Files.isSymbolicLink(link)) {
					Path dest = Files.readSymbolicLink(link);
					return createJdk(dest);
				} else {
					try (Stream<Path> jdkPaths = listJdkPaths()) {
						return jdkPaths	.filter(p -> JavaUtil.parseJavaVersion(p.getFileName().toString()) > 0)
										.filter(sameJdk(link))
										.map(this::createJdk)
										.filter(Objects::nonNull)
										.findAny()
										.orElse(null);
					}

				}
			}
		} catch (IOException ex) {
			// Ignore
		}
		return null;
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
	protected abstract Path getDefaultJdkPath();

	@Nullable
	protected Jdk createJdk(Path home) {
		String name = home.getFileName().toString();
		// Make sure folders start with a number
		if (JavaUtil.parseJavaVersion(name) > 0) {
			Optional<String> version = resolveJavaVersionStringFromPath(home);
			if (version.isPresent()) {
				return createJdk(jdkId(name), version.get(), home);
			}
		}
		return null;
	}

	protected abstract String jdkId(String name);

	public static Optional<Integer> resolveJavaVersionFromPath(Path home) {
		return resolveJavaVersionStringFromPath(home).map(JavaUtil::parseJavaVersion);
	}

	public static Optional<String> resolveJavaVersionStringFromPath(Path home) {
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
}
