package dev.jbang.net.jdkproviders;

import static dev.jbang.net.jdkproviders.BaseFoldersJdkProvider.resolveJavaVersionStringFromPath;
import static dev.jbang.util.JavaUtil.parseJavaOutput;
import static dev.jbang.util.JavaUtil.parseJavaVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

/**
 * This JDK provider detects if a JDK is already available on the system by
 * first looking at <code>JAVA_HOME</code> and if that doesn't exist it checks
 * the user's <code>PATH</code>.
 */
public class EnvJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public SortedSet<Jdk> listInstalled() {
		Path jdkHome = JavaUtil.getJdkHome();
		if (jdkHome == null || !Files.isDirectory(jdkHome)) {
			Path javac = Util.searchPath("javac");
			if (javac != null) {
				try {
					javac = javac.toRealPath();
					jdkHome = javac.getParent().getParent();
				} catch (IOException e) {
					// Ignoring any errors
				}
			}
		}
		if (jdkHome != null) {
			Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				String id = "default-env";
				return new TreeSet<>(Collections.singleton(createJdk(id, jdkHome,
						jdk -> resolveJavaVersionStringFromPath(jdk.getHome()))));
			}
		}
		return Collections.emptySortedSet();
	}

	@Nullable
	@Override
	public Jdk getJdkByPath(@Nonnull Path jdkPath) {
		Jdk def = getDefault();
		return jdkPath.startsWith(def.getHome()) ? def : null;
	}

	@Nullable
	@Override
	public Jdk getDefault() {
		SortedSet<Jdk> installed = listInstalled();
		return !installed.isEmpty() ? installed.first() : null;
	}

	// TODO most likely not needed anymore and can be removed
	private static int determineJavaVersion(Path javaCmd) {
		String output = Util.runCommand(javaCmd.toString(), "-version");
		int version = parseJavaVersion(parseJavaOutput(output));
		if (version == 0) {
			Util.verboseMsg(
					"Version could not be determined from: '$javaCmd -version', trying 'java.version' property");
			version = parseJavaVersion(System.getProperty("java.version"));
		}
		return version;
	}
}
