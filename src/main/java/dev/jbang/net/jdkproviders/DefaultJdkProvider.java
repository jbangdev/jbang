package dev.jbang.net.jdkproviders;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Files;
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
		Path defaultDir = Settings.getCurrentJdkDir();
		if (Files.isDirectory(defaultDir)) {
			Optional<String> version = resolveJavaVersionStringFromPath(defaultDir);
			if (version.isPresent()) {
				String id = "default";
				return Collections.singletonList(createJdk(id, defaultDir, version.get()));
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
