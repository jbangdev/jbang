package dev.jbang.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class NativeImageSanityCheckIT extends BaseIT {

	@Test
	public void runningNativeImageWhenWeNeedTo() {

		boolean useNative = Boolean.parseBoolean(System.getenv("JBANG_USE_NATIVE"));

		if (useNative) {
			assertThat(System.getProperty("java.home")).as("java.home should be null when using native image").isNull();
		} else {
			assertThat(System.getProperty("java.home")).as("java.home should be set when not using native image")
				.isNotNull();
		}
	}

}