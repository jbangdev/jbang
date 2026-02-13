package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class RunScalaIT extends BaseIT {

	@Test
	public void shouldRunScala() {
		assertThat(shell("jbang runscala.scala")).outContains("SUCCESS!").exitedWith(0);
	}

}
