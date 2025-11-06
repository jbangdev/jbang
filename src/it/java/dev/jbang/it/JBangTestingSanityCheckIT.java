package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.Test;

public class JBangTestingSanityCheckIT extends BaseIT {

	@Test
	public void runningNativeImageWhenWeNeedTo() {

		boolean useNative = Boolean.parseBoolean(System.getenv("JBANG_USE_NATIVE"));

		CommandResultAssert at = assertThat(shell("jbang version --verbose")).succeeded();

		if (useNative) {
			at.errContains("Java: null [").as("java.home should be null when using native image");
		} else {
			at.errNotContains("Java: null [").as("java.home should be set when not using native image");
		}
	}

	@Test
	public void runningJBangWithExpectedJavaVersion() {

		boolean useNative = Boolean.parseBoolean(System.getenv("JBANG_USE_NATIVE"));

		CommandResultAssert at = assertThat(shell("jbang version --verbose")).succeeded();

		if (useNative) {
			at.errFind(" \\[25\\...*\\]")
				.as("java.version is hardcoded to 25.x when using native image");
		} else {
			String javaVersion = Objects.toString(System.getenv("_JBANG_TEST_JAVA_VERSION"),
					Integer.toString(Runtime.version().feature()));
			if ("8".equals(javaVersion)) {
				javaVersion = "1.8";
			}
			at.errFind("Java: .* \\[" + javaVersion + "\\..*\\]")
				.as("java.version should be set when not using native image");
		}
	}

}