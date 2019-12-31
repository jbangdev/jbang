package dk.xam.jbang;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class TestInit {

	@Test
	void testInit() {

		var m = new Main();

		String s = m.renderInitClass(new File("test.java"));

		assertThat(s, containsString("class test"));

	}
}
