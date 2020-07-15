package dev.jbang;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class TestJdkManager {
	// TODO: Remove! Not an actual test!!
	@Test
	void testGetCurrentJdk() throws IOException {
		Path jdk = JdkManager.getCurrentJdk("14+");
	}
}
