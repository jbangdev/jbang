package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.BaseTest;
import dev.jbang.util.Util;

public class TestInit extends BaseTest {

	@Test
	void testInit() {

		String s = new Init().renderInitClass(new File("test.java"), "hello");

		assertThat(s, containsString("class test"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "bad.name.java", "Bad-Name.java" })
	void testInvalidInit(String filename) {

		Exception ex = assertThrows(ExitException.class, () -> {
			new Init().renderInitClass(new File(filename), "hello");
		});

		assertThat(ex.getMessage(), containsString("is not a valid class name in java."));
	}

	@Test
	void testCli(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		Jbang.getCommandLine().execute("init", "--template=cli", s);
		assertThat(new File(s).exists(), is(true));

		MatcherAssert.assertThat(Util.readString(x), containsString("picocli"));

	}

	@Test
	void testOldInit(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("nonexist.java");
		String s = x.toString();
		assertEquals(Jbang.getCommandLine().execute("--init", s), 2);
	}

	@Test
	void testMissingTemplate(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = Jbang.getCommandLine().execute("init", "--template=bogus", s);
		assertThat(new File(s).exists(), is(false));
		assertThat(result, not(0));
	}

	@Test
	void testDefaultInit(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class edit"));
	}

	@Test
	void testInitExtensionlessKebab(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("xyz-plug");
		String s = x.toString();
		int result = Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class XyzPlug"));
	}

	@Test
	void testInitExtensionless(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("xyzplug");
		String s = x.toString();
		int result = Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class xyzplug"));
	}

	@Test
	void testCatalog() throws IOException {
//		int result = Jbang.getCommandLine().execute("run", "jget@quintesse");
//		int result = Jbang.getCommandLine().execute("catalog", "update");
		Jbang.getCommandLine().execute("catalog", "list", "quintesse");
	}

}
