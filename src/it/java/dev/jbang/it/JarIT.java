package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

public class JarIT extends BaseIT {

	@Test
	@Description("java launch file")
	public void shouldLaunchJarFile() {
		assertThat(shell("jbang hellojar.jar"))
												.outIsExactly("Hello World\n");
	}

	@Test
	@Description("java launch GAV")
	public void shouldLaunchGAV() {
		assertThat(shell(
				"jbang --main picocli.codegen.aot.graalvm.ReflectionConfigGenerator info.picocli:picocli-codegen:4.6.3"))
																															.succeeded()
																															.errContains(
																																	"Missing required parameter: '<classes>'");
	}
}