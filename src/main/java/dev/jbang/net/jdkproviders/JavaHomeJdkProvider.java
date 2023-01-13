package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;

/**
 * This JDK provider detects if a JDK is already available on the system by
 * looking at <code>JAVA_HOME</code> environment variable.
 */
public class JavaHomeJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public List<Jdk> listInstalled() {
		Path jdkHome = JavaUtil.getJdkHome();
		if (jdkHome != null && Files.isDirectory(jdkHome)) {
			Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				String id = "javahome";
				return Collections.singletonList(createJdk(id, jdkHome, version.get()));
			}
		}
		return Collections.emptyList();
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
