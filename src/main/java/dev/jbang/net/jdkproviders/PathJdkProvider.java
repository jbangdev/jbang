package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

/**
 * This JDK provider detects if a JDK is already available on the system by
 * first looking at the user's <code>PATH</code>.
 */
public class PathJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public List<Jdk> listInstalled() {
		// Explanation of the heuristics used:
		// We first look for "java" on the user's PATH,
		// then we see if we can find a "javac" in the
		// same folder. If so, we assume they belong to a
		// single JDK which we create and return. If not,
		// we try to find "javac" anywhere on the PATH,
		// and if we find it, we assume it to be part of
		// a different JDK, we create two JDKs, one for
		// each, and we return both.

		Map<Path, Path> paths = new HashMap<>();
		Path java = Util.searchPath("java");
		Path javac;
		if (java != null) {
			java = java.toAbsolutePath();
			Path jdkHome = JavaUtil.jre2jdk(java.getParent().getParent());
			paths.put(jdkHome, java);
			javac = Util.searchPath("javac", java.getParent().toString());
		} else {
			javac = Util.searchPath("javac");
		}
		if (javac != null) {
			javac = javac.toAbsolutePath();
			Path jdkHome = JavaUtil.jre2jdk(javac.getParent().getParent());
			paths.put(jdkHome, javac);
		}

		if (paths.isEmpty()) {
			return Collections.emptyList();
		}

		return paths.entrySet().stream().map(e -> {
			// Assure that the command is located in a "bin" folder
			// (it can't be a true JDK otherwise)
			Path cmd = e.getValue();
			if (cmd.getParent().getFileName().toString().equals("bin")) {
				Path jdkHome = e.getKey();
				Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
				if (version.isPresent()) {
					String id = paths.size() == 1 ? "path" : "path-" + cmd.getFileName();
					return createJdk(id, jdkHome, version.get());
				}
			}
			return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Nullable
	@Override
	public Jdk getJdkById(@Nonnull String id) {
		if (id.equals(name())) {
			List<Jdk> l = listInstalled();
			if (!l.isEmpty()) {
				return l.get(0);
			}
		}
		return null;
	}

	@Nullable
	@Override
	public Jdk getJdkByPath(@Nonnull Path jdkPath) {
		List<Jdk> installed = listInstalled();
		Jdk def = !installed.isEmpty() ? installed.get(0) : null;
		return def != null && def.getHome() != null && jdkPath.startsWith(def.getHome()) ? def : null;
	}
}
