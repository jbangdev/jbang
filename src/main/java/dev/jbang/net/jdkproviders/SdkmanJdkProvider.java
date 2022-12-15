package dev.jbang.net.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This JDK provider detects any JDKs that have been installed using the SDKMAN
 * package manager.
 */
public class SdkmanJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get(System.getProperty("user.home")).resolve(".sdkman/candidates/java");

	@Nonnull
	@Override
	protected Path getJdksRoot() {
		return JDKS_ROOT;
	}

	@Nullable
	@Override
	protected String jdkId(String name) {
		return name + "-sdkman";
	}

	@Override
	public boolean canUse() {
		return Files.isDirectory(JDKS_ROOT);
	}
}
