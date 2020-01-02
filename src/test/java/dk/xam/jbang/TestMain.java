package dk.xam.jbang;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMain {

	public static final String EXAMPLES_FOLDER = "examples";
	static File examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException {
		URL examplesUrl = TestMain.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		examplesTestFolder = new File(new File(examplesUrl.toURI()).getAbsolutePath());
	}

	@Test
	void testHelloWorld() throws FileNotFoundException {

		Main main = new Main();
		var arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		new CommandLine(main).parseArgs(arg);

		String result = main.generateCommandLine(new Script(new File("helloworld.java"), "")).toString();

		assertThat(result, startsWith("java"));
		assertThat(result, containsString("helloworld.java"));
		assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldShell() throws FileNotFoundException {

		Main main = new Main();
		var arg = new File(examplesTestFolder, "helloworld.jsh").getAbsolutePath();
		new CommandLine(main).parseArgs(arg);

		String result = main.generateCommandLine(new Script(new File("helloworld.jsh"), "")).toString();

		assertThat(result, startsWith("jshell"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("helloworld.jsh"));
		assertThat(result, not(containsString("--source 11")));
	}

	@Test
	void testDebug() throws FileNotFoundException {

		Main main = new Main();
		var arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		new CommandLine(main).parseArgs("--debug", arg);

		String result = main.generateCommandLine(new Script(new File("helloworld.java"), "")).toString();

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("helloworld.java"));
		assertThat(result, containsString(" --source 11 "));
		assertThat(result, containsString("jdwp"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, not(containsString("classpath")));
	}

	@Test
	void testDependencies() throws FileNotFoundException {

		Main main = new Main();
		var arg = new File(examplesTestFolder, "classpath_example.java").getAbsolutePath();
		new CommandLine(main).parseArgs(arg);

		String result = main.generateCommandLine(new Script(new File(arg))).toString();

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("classpath_example.java"));
		assertThat(result, containsString(" --source 11 "));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("classpath"));
		assertThat(result, containsString("log4j"));
	}

	@Test
	void testURLPrepare() throws IOException {

		var url = new File(examplesTestFolder, "classpath_example.java").toURI().toString();

		var result = Main.prepareScript(url);

		assertThat(result.toString(), not(containsString(url)));

		assertThat(Files.readString(result.backingFile.toPath()),
				containsString("Logger.getLogger(classpath_example.class);"));

		Main main = new Main();
		new CommandLine(main).parseArgs(url.toString());

		String s = main.generateCommandLine(Main.prepareScript(url.toString()));

		assertThat(s, not(containsString("file:")));
	}

	@Test
	void testURLPrepareDoesNotExist() throws IOException {

		var url = new File(examplesTestFolder, "classpath_example.java.dontexist").toURI().toString();

		assertThrows(ExitException.class, () -> Main.prepareScript(url));

	}

}
