package dk.xam.jbang;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class TestEdit {

	private CommandLine cli;
	private Main main;
	StringWriter output;

	@BeforeEach
	void setup() {
		output = new StringWriter();
		cli = Main.getCommandLine(new PrintWriter(output), new PrintWriter(output));
		main = cli.getCommand();
	}

	@Test
	void testEdit(@TempDir Path outputDir) throws IOException {

		String s = outputDir.resolve("edit.java").toString();
		Main.getCommandLine().execute("--init", s);
		assertThat(new File(s).exists(), is(true));

		Script script = new Script(new File(s));

		File project = main.createProject(script, Collections.emptyList(), script.collectDependencies());

		assertThat(new File(project, "src"), FileMatchers.anExistingDirectory());
		File build = new File(project, "build.gradle");
		assert (build.exists());
		assertThat(Util.readString(build.toPath()), containsString("dependencies"));
		File src = new File(project, "src/edit.java");
		assert (src.exists());
		assert (Files.isSymbolicLink(src.toPath()));

	}
}
