package dev.jbang.net.jdkproviders;

import static dev.jbang.net.jdkproviders.BaseFoldersJdkProvider.resolveJavaVersionStringFromPath;

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
		if (jdkHome != null) {
			Optional<String> version = resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				String id = "default-javahome";
				return Collections.singletonList(createJdk(id, jdkHome, jdk -> version));
			}
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public Jdk getJdkByPath(@Nonnull Path jdkPath) {
		Jdk def = getDefault();
		return def != null && def.getHome() != null && jdkPath.startsWith(def.getHome()) ? def : null;
	}

	@Nullable
	@Override
	public Jdk getDefault() {
		List<Jdk> installed = listInstalled();
		return !installed.isEmpty() ? installed.get(0) : null;
	}
}
