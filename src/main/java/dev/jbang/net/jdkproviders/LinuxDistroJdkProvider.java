package dev.jbang.net.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This JDK provider is intended to detects JDKs that have been installed in
 * standard location of the users linux distro.
 *
 * For now just using `/usr/lib/jvm` as apparently fedora, debian, ubuntu and
 * centos/rhel use it.
 *
 * If need different behavior per linux distro its intended this provider will
 * adjust based on identified distro.
 *
 */
public class LinuxDistroJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get("/usr/lib/jvm");

	@Nonnull
	@Override
	protected Path getJdksRoot() {
		return JDKS_ROOT;
	}

	@Nullable
	@Override
	protected String jdkId(String name) {
		return name + "-nixdistro";
	}

	@Override
	public boolean canUse() {
		return Files.isDirectory(JDKS_ROOT);
	}
}
