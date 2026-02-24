package dev.jbang.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import io.qameta.allure.Description;

/**
 * Integration test to verify that jbang downloads the correct musl-based Java
 * when running on Alpine Linux containers.
 * 
 * This test uses testcontainers to run jbang in an Alpine container and
 * verifies that the downloaded Java actually runs (which would fail if it's
 * glibc-based).
 */
@Testcontainers
@DisabledIf(value = "shouldDisableTest", disabledReason = "Disabled on GitHub Actions Mac/Windows or when Docker/Podman is not available")
public class AlpineJavaDownloadIT extends BaseIT {

	private static Path jbangDistPath;

	/**
	 * Checks if the test should be disabled. This method is used by @DisabledIf
	 * annotation.
	 * 
	 * The test is disabled if: 1. Running on GitHub Actions Mac or Windows runners
	 * 2. Running locally and neither Docker nor Podman is available
	 */
	@SuppressWarnings("unused")
	public static boolean shouldDisableTest() {
		String githubActions = System.getenv("GITHUB_ACTIONS");
		boolean isGitHubActions = "true".equals(githubActions);

		// Check if we're on GitHub Actions Mac or Windows
		if (isGitHubActions) {
			String runnerOs = System.getenv("RUNNER_OS");
			if ("macOS".equals(runnerOs) || "Windows".equals(runnerOs)) {
				return true; // Disable on GitHub Actions Mac/Windows
			}
			// On GitHub Actions Linux, don't disable (Docker should be available)
			return false;
		}

		// Not in GitHub Actions - check if Docker or Podman is available locally
		// using Testcontainers API
		try {
			DockerClientFactory.instance().client();
			return false; // Docker/Podman is available via Testcontainers, don't disable
		} catch (Exception e) {
			// Docker/Podman is not available, disable the test
			return true;
		}
	}

	@BeforeAll
	public static void setupJbangDistribution(@TempDir Path tempDir) throws IOException {
		// Find the jbang distribution that was built
		jbangDistPath = Paths.get("build/install/jbang");
		if (!Files.exists(jbangDistPath)) {
			throw new IllegalStateException(
					"JBang distribution not found at " + jbangDistPath
							+ ". Run 'gradlew installDist' first.");
		}
	}

	@Container
	@SuppressWarnings("resource")
	public GenericContainer<?> container = new GenericContainer<>(
			new ImageFromDockerfile("jbang-alpine-test", false)
				.withDockerfileFromBuilder(builder -> builder
					// .from("alpine:latest")
					.from("ghcr.io/home-assistant/amd64-base:3.19")
					.run("apk add --no-cache bash curl")
					.build()))
		.withStartupTimeout(Duration.ofMinutes(5))
		.withCommand("tail", "-f", "/dev/null"); // Keep container running

	@Test
	@Description("Verify that jbang downloads musl-based Java on Alpine Linux")
	public void testAlpineJavaDownload() throws Exception {

		// Copy jbang distribution into container
		container.copyFileToContainer(
				MountableFile.forHostPath(jbangDistPath), "/jbang");

		// assertThat(container.execInContainer("tree",
		// "/jbang").getStdout()).isEqualTo("");

		// Make jbang executable
		assertThat(
				container.execInContainer("chmod", "+x", "/jbang/bin/jbang")
					.getExitCode())
			.isEqualTo(0);

		// Run jbang without JAVA_HOME set - this should trigger Java download
		// Clear any existing JAVA_HOME
		ExecResult result = container.execInContainer("sh", "-c",
				"unset JAVA_HOME && /jbang/bin/jbang version");

		assertThat(result.getStderr()).as("Downloaded Java is not musl compatible")
			.doesNotContain("java: cannot execute");

		assertThat(result.getStdout()).startsWith("0.");
		// Verify the command succeeded
		assertThat(result.getExitCode())
			.as("JBang should successfully download and run Java on Alpine enought to print version")
			.isEqualTo(0);

		// Verify that Java was actually downloaded (check cache directory)
		result = container.execInContainer("sh", "-c",
				"ls -la /root/.jbang/cache/jdks/ 2>/dev/null || echo 'no jdks found'");
		String jdkList = result.getStdout() + result.getStderr();
		assertThat(jdkList)
			.as("Java JDK should be downloaded to cache")
			.doesNotContain("no jdks found");

		// Verify the downloaded Java actually runs (this would fail if it's
		// glibc-based)
		result = container.execInContainer("sh", "-c",
				"find /root/.jbang/cache/jdks -name java -type f | head -1 | xargs -I {} {} -version");
		assertThat(result.getExitCode())
			.as("Downloaded Java should be executable on Alpine (musl-based)")
			.isEqualTo(0);
		assertThat(result.getStdout() + result.getStderr())
			.as("Java version should be printed")
			.contains("version");
	}

}
