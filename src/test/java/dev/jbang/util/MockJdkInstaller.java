package dev.jbang.util;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.cli.TestJdk;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkInstaller;
import dev.jbang.devkitman.JdkInstallers;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.JavaUtils;

public class MockJdkInstaller implements JdkInstaller {
	private JdkProvider provider;
	private String[] versions;

	private static final Logger LOGGER = Logger.getLogger(MockJdkInstaller.class.getName());

	public MockJdkInstaller(@NonNull JdkProvider provider, @NonNull String... versions) {
		this.provider = provider;
		this.versions = versions;
	}

	@Override
	public @NonNull Stream<Jdk.AvailableJdk> listAvailable() {
		return Arrays.stream(versions)
			.map(v -> new Jdk.AvailableJdk.Default(provider, v, v, Collections.emptySet()));
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk, Path installDir) {
		TestJdk.initMockJdkDir(installDir, jdk.version());
		LOGGER.log(Level.INFO, "Installing Mock JDK {0}...", jdk.version());
		Jdk.InstalledJdk newJdk = provider.createJdk(jdk.id(), installDir);
		if (newJdk == null) {
			throw new IllegalStateException("Cannot install mock JDK: " + jdk.version());
		}
		return newJdk;
	}

	@Override
	public List<JdkDistro> listDistros() {
		return Collections.emptyList();
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		JavaUtils.safeDeleteJdk(jdk.home());
	}

	public static class Discovery implements JdkInstallers.Discovery {
		@Override
		public @NonNull String name() {
			return "mock";
		}

		@Override
		public @NonNull JdkInstaller create(Config config) {
			String[] versions = config.properties().getOrDefault("versions", "17").split(",");
			MockJdkInstaller installer = new MockJdkInstaller(config.jdkProvider(), versions);
			return installer;
		}
	}
}
