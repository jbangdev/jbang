package dk.xam.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.File;

import org.junit.jupiter.api.Test;

public class TestInit {

	@Test
	void testInit() {

		Main m = new Main();

		String s = m.renderInitClass(new File("test.java"));

		assertThat(s, containsString("class test"));

	}
}
