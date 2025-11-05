package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.jbang.util.JavaUtil;

import io.qameta.allure.Description;

public class CDSIT extends BaseIT {

	@Test
	@Description("Testing that CDS generates JSA file and uses it on subsequent runs")
	@Disabled("Re-enable when https://github.com/jbangdev/jbang/issues/2272 is fixed")
	public void testCDSGeneratesJSAFile() throws IOException {
		// CDS is only available on Java 13+
		if (JavaUtil.getCurrentMajorJavaVersion() < 13) {
			// On older Java versions, should show warning
			assertThat(shell("jbang --cds echo.java"))
				.errContains("ClassDataSharing can only be used on Java versions 13 and later");
			return;
		}

		// First run: should create JSA file
		assertThat(shell("jbang --verbose --cds echo.java"))
			.succeeded()
			.errContains("-XX:SharedArchiveFile=")
			.errNotContains("-XX:ArchiveClassesAtExit=")
			.errContains("CDS: Archiving Classes At Exit");

		// Find the JSA file in the cache
		Path jarsCache = scratch().resolve("itest-jbang").resolve("cache").resolve("jars");
		Path jsaFile = findJSAFile(jarsCache, "echo");
		assertThat(jsaFile).as("JSA file should exist after first run with CDS")
			.isNotNull()
			.exists();

		// Second run: should use existing JSA file
		assertThat(shell("jbang --verbose --cds echo.java"))
			.succeeded()
			.errContains("CDS: Using shared archive classes");
	}

	private Path findJSAFile(Path jarsCache, String name) throws IOException {
		if (!Files.exists(jarsCache)) {
			return null;
		}
		try (Stream<Path> paths = Files.walk(jarsCache)) {
			return paths
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().equals(name + ".jsa"))
				.findFirst()
				.orElse(null);
		}
	}
}
