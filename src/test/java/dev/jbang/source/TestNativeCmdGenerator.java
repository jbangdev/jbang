package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

/**
 * A native image is run directly, so anything JBang wants the program to see
 * has to be on its command line. These tests pin what does and does not get
 * passed.
 */
public class TestNativeCmdGenerator extends BaseTest {

	/**
	 * Builds the command line for a native run of the given script without actually
	 * invoking the native image compiler: an empty file at the expected image
	 * location is enough for the generator to take its normal path instead of
	 * falling back to java mode.
	 */
	private String generateNativeCmd(Path script, java.util.Map<String, String> properties,
			java.util.List<String> runtimeOptions) throws IOException {
		ProjectBuilder pb = Project.builder().nativeImage(true);
		if (properties != null) {
			pb.setProperties(properties);
		}
		Project prj = pb.build(script.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		Path image = ctx.getNativeImageFile();
		Files.createDirectories(image.getParent());
		Files.write(image, new byte[0]);
		return CmdGenerator.builder(ctx)
			.runtimeOptions(runtimeOptions)
			.build()
			.generate();
	}

	@Test
	void testNativePassesProperties() throws IOException {
		Path script = examplesTestFolder.resolve("quote.java").toAbsolutePath();
		String cmd = generateNativeCmd(script, Collections.singletonMap("foo", "bar"), null);

		assertThat(cmd, containsString("-Dfoo=bar"));
	}

	@Test
	void testNativePassesPropertiesBeforeArguments() throws IOException {
		Path script = examplesTestFolder.resolve("quote.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder().nativeImage(true);
		pb.setProperties(Collections.singletonMap("foo", "bar"));
		Project prj = pb.build(script.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		Path image = ctx.getNativeImageFile();
		Files.createDirectories(image.getParent());
		Files.write(image, new byte[0]);

		String cmd = CmdGenerator.builder(ctx)
			.setArguments(Collections.singletonList("anarg"))
			.build()
			.generate();

		// the image runtime consumes -D from anywhere, but keeping the flags in
		// front of the user's arguments matches what the jar generator does
		assertThat(cmd.indexOf("anarg"), greaterThan(cmd.indexOf("-Dfoo=bar")));
		assertThat(cmd.indexOf("-Dfoo=bar"), greaterThan(cmd.indexOf(".bin")));
	}

	@Test
	void testNativePassesDashDRuntimeOptions() throws IOException {
		Path script = examplesTestFolder.resolve("quote.java").toAbsolutePath();
		String cmd = generateNativeCmd(script, null, Collections.singletonList("-Dfrom=option"));

		assertThat(cmd, containsString("-Dfrom=option"));
	}

	@Test
	void testNativeIgnoresOtherRuntimeOptions() throws IOException {
		Path script = examplesTestFolder.resolve("quote.java").toAbsolutePath();
		String cmd = generateNativeCmd(script, null, Collections.singletonList("-Xmx128m"));

		// a native image either rejects unknown JVM flags outright or leaves them in
		// place, where they arrive as program arguments; neither is useful
		assertThat(cmd, not(containsString("-Xmx128m")));
	}
}
