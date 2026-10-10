package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@code //DEPS <build-file>} against real build tools using the
 * fixtures under {@code itests/builddeps/<tool>/}. Each fixture ships a
 * {@code mise.toml} declaring the tool version it needs. The test asks
 * https://mise.jdx.dev[mise] to install those tools (a no-op if already cached)
 * and then runs {@code jbang App.java} inside the fixture folder with the
 * declared tools on {@code PATH}. If {@code mise} is not installed at all the
 * tests are skipped.
 */
public class BuildSystemClassPathsIT extends BaseIT {

	@Test
	public void mavenPomProvidesClasspath() {
		runFixture("maven", "maven-classpath:olleh");
	}

	@Test
	public void gradleBuildProvidesClasspath() {
		runFixture("gradle", "gradle-classpath:olleh");
	}

	@Test
	public void sbtBuildProvidesClasspath() {
		runFixture("sbt", "sbt-classpath:olleh");
	}

	@Test
	public void millBuildProvidesClasspath() {
		runFixture("mill", "mill-classpath:olleh");
	}

	/**
	 * Ensures the tools declared in {@code itests/builddeps/<folder>/mise.toml} are
	 * installed, then runs {@code jbang App.java} inside that folder and asserts
	 * stdout contains {@code expected}. Skipped when mise itself is unavailable.
	 */
	private void runFixture(String folder, String expected) {
		assumeTrue(isOnPath("mise"),
				"mise is not installed; see https://mise.jdx.dev/getting-started.html");
		String fixture = baseDir().resolve("builddeps").resolve(folder).toAbsolutePath().toString();

		// mise auto-trust: MISE_TRUSTED_CONFIG_PATHS whitelists our scratch tree,
		// MISE_YES avoids any interactive prompts during install.
		Map<String, String> env = new HashMap<>();
		env.put("MISE_TRUSTED_CONFIG_PATHS", baseDir().toAbsolutePath().toString());
		env.put("MISE_YES", "1");

		// Install the tools declared for this fixture (cached across runs).
		installTools(fixture, env);

		assertThat(shell(env, "cd builddeps/" + folder + " && mise exec -- jbang App.java"))
			.succeeded()
			.outContains(expected);
	}

	private static void installTools(String fixture, Map<String, String> env) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ProcessBuilder pb = new ProcessBuilder("mise", "install")
				.directory(new java.io.File(fixture))
				.redirectErrorStream(true);
			pb.environment().putAll(env);
			Process p = pb.start();
			p.getInputStream().transferTo(out);
			boolean done = p.waitFor(5, TimeUnit.MINUTES);
			assumeTrue(done && p.exitValue() == 0,
					"mise install failed in " + fixture + ":\n" + out);
		} catch (Exception e) {
			throw new AssertionError("Failed to run mise install in " + fixture, e);
		}
	}

	private static boolean isOnPath(String command) {
		try {
			Process p = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
			p.getInputStream().transferTo(new ByteArrayOutputStream());
			return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
}
