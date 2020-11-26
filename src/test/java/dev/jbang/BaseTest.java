package dev.jbang;

import java.io.IOException;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.rules.TemporaryFolder;

public abstract class BaseTest {

	@BeforeEach
	void initEnv() throws IOException {
		jbangTempDir.create();
		environmentVariables.set("JBANG_DIR", jbangTempDir.getRoot().getPath());
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Rule
	public final TemporaryFolder jbangTempDir = new TemporaryFolder();
}
