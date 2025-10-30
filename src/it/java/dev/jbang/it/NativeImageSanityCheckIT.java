package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class NativeImageSanityCheckIT extends BaseIT {

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

}