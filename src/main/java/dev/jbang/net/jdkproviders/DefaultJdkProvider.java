package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Settings;
import dev.jbang.net.JdkProvider;

/**
 * This JDK provider returns the "default" JDK if it was set (using
 * <code>jbang jdk default</code>).
 */
public class DefaultJdkProvider implements JdkProvider {
	@Nonnull
	@Override
	public List<Jdk> listInstalled() {
		Path jdkHome = Settings.getCurrentJdkDir();
		if (jdkHome != null) {
			Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				String id = "default";
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
