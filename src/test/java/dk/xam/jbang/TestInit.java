package dk.xam.jbang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestInit {

	@Test
	void testInit() {

		Main m = new Main();

		String s = m.renderInitClass(new File("test.java"), "hello");

		assertThat(s, containsString("class test"));
	}

	@Test
	void testCli(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		Main.getCommandLine().execute("--init=cli", s);
		assertThat(new File(s).exists(), is(true));

		assertThat(Util.readString(x), containsString("picocli"));

	}

	@Test
	void testMissingTemplate(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = Main.getCommandLine().execute("--init=bogus", s);
		assertThat(new File(s).exists(), is(false));
		assertThat(result, not(0));
	}

	@Test
	void testDefaultInit(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = Main.getCommandLine().execute("--init", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class edit"));
	}

	@Test
	void testInitExtensionlessKebab(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("xyz-plug");
		String s = x.toString();
		int result = Main.getCommandLine().execute("--init=", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class XyzPlug"));
	}

	@Test
	void testInitExtensionless(@TempDir Path outputDir) throws IOException {

		Path x = outputDir.resolve("xyzplug");
		String s = x.toString();
		int result = Main.getCommandLine().execute("--init=", s);
		assertThat(new File(s).exists(), is(true));
		assertThat(result, is(0));

		assertThat(Util.readString(x), containsString("class xyzplug"));
	}

}
