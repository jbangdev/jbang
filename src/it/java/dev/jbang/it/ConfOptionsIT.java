package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

/**
 * Integration tests for the //CONF directive, which applies key=value pairs as
 * -D system properties on the launched JVM.
 */
public class ConfOptionsIT extends BaseIT {

	@Test
	@Description("//CONF lines should be applied as -D system properties on the launched JVM")
	public void confOptionsSetSystemProperties() {
		assertThat(shell("jbang run confoptions.java"))
			.succeeded()
			.outContains("JVMARG:-Dlogback.file.name=app.log")
			.outContains("JVMARG:-Dlogback.file.maxSize=1MB")
			.outContains("JVMARG:-Dspring.http.client.connect-timeout=5s")
			.outContains("PROP:logback.file.name=app.log")
			.outContains("PROP:logback.file.maxSize=1MB")
			.outContains("PROP:spring.http.client.connect-timeout=5s");
	}

	@Test
	@Description("Duplicate //CONF keys should resolve to the last value declared in the file")
	public void duplicateConfKeyLastValueWins() {
		assertThat(shell("jbang run confoptions.java"))
			.succeeded()
			.outContains("JVMARG:-Dspring.http.client.read-timeout=10s")
			.outContains("JVMARG:-Dspring.http.client.read-timeout=20s")
			.outContains("PROP:spring.http.client.read-timeout=20s");
	}

	@Test
	@Description("A duplicate //CONF key should produce a warning on stderr")
	public void duplicateConfKeyWarnsOnStderr() {
		assertThat(shell("jbang run --fresh confoptions.java"))
			.succeeded()
			.errContains("spring.http.client.read-timeout");
	}
}
