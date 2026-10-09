package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in native image tests. The build of the experimental image is
 * intentionally separate from the normal integrationTest task.
 */
@EnabledIfEnvironmentVariable(named = "JBANG_CREMA_BINARY", matches = ".+")
public class CremaIT extends BaseIT {
	private static Path binary;

	@BeforeAll
	public static void requireCremaBinary() {
		String supplied = System.getenv("JBANG_CREMA_BINARY");
		binary = Paths.get(supplied).toAbsolutePath();
		assertTrue(Files.isRegularFile(binary), "No Crema binary at " + binary);
	}

	private CommandResult crema(String... arguments) {
		List<String> command = new ArrayList<>();
		command.add(binary.toString());
		command.add("crema");
		command.addAll(Arrays.asList(arguments));
		return run(baseDir(), baseEnv, command);
	}

	@Test
	public void argumentsAndCachedExecution() {
		for (int i = 0; i < 2; i++) {
			assertThat(crema("crema/Hello.java", "two words", "--flag"))
				.exitedWith(0)
				.outContains("two words|--flag");
		}
	}

	@Test
	public void dependencyFromMaven() {
		assertThat(crema("crema/Dependency.java"))
			.exitedWith(0)
			.outContains("\"dependency\"");
	}

	@Test
	public void resourceOnApplicationThread() {
		assertThat(crema("crema/Resource.java"))
			.exitedWith(0)
			.outContains("resource");
	}

	@Test
	public void exitStatus() {
		assertThat(crema("crema/Exit.java")).exitedWith(7);
	}

	@Test
	public void applicationFailure() {
		assertThat(crema("crema/Broken.java"))
			.exitedWith(1)
			.errContains("boom");
	}
}
