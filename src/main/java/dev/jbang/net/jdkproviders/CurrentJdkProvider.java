package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.JavaUtil;

/**
 * This JDK provider returns the "current" JDK, which is the JDK that is
 * currently being used to run JBang.
 */
public class CurrentJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public List<Jdk> listInstalled() {
		String jh = System.getProperty("java.home");
		if (jh != null) {
			Path jdkHome = Paths.get(jh);
			jdkHome = JavaUtil.jre2jdk(jdkHome);
			Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				String id = "current";
				return Collections.singletonList(createJdk(id, jdkHome, version.get()));
			}
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public Jdk getJdkByPath(@Nonnull Path jdkPath) {
		List<Jdk> installed = listInstalled();
		Jdk def = !installed.isEmpty() ? installed.get(0) : null;
		return def != null && def.getHome() != null && jdkPath.startsWith(def.getHome()) ? def : null;
	}
}
