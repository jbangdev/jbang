package dk.xam.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

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

		File project = main.createProject(script, Collections.emptyList());

		assertThat(new File(project, "src"), FileMatchers.anExistingDirectory());
		File build = new File(project, "build.gradle");
		assert (build.exists());
		assertThat(Util.readString(build.toPath()), containsString("dependencies"));
		File src = new File(project, "src/edit.java");
		assert (src.exists());
		assert (Files.isSymbolicLink(src.toPath()));

		// check eclipse is there
		assertThat(Arrays.stream(project.listFiles()).map(File::getName).collect(Collectors.toList()),
				containsInAnyOrder(".project", ".classpath", ".eclipse", "src", "build.gradle", ".vscode"));
		File launchfile = new File(project, ".eclipse/edit.launch");
		assert (launchfile.exists());
		assertThat(Util.readString(launchfile.toPath()), containsString("launching.PROJECT_ATTR"));
		assertThat(Util.readString(launchfile.toPath()), containsString("PROGRAM_ARGUMENTS\" value=\"\""));

		launchfile = new File(project, ".eclipse/edit-port-4004.launch");
		assert (launchfile.exists());
		assertThat(Util.readString(launchfile.toPath()), containsString("launching.PROJECT_ATTR"));
		assertThat(Util.readString(launchfile.toPath()), containsString("4004"));
	}

	@Test
	void testEditDeps(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("edit.java");
		String s = p.toString();
		Main.getCommandLine().execute("--init", s);
		assertThat(new File(s).exists(), is(true));

		Util.writeString(p, "//DEPS org.openjfx:javafx-graphics:11.0.2:${os.detected.jfxname}\n" + Util.readString(p));

		Script script = new Script(new File(s));

		File project = main.createProject(script, Collections.emptyList());

		File gradle = new File(project, "build.gradle");
		assert (gradle.exists());
		assertThat(Util.readString(gradle.toPath()), not(containsString("os.detected.jfxname")));

	}

}
