package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aReadableFile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.hamcrest.MatcherAssert;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.*;

public class TestEdit extends BaseTest {

	StringWriter output;

	@BeforeEach
	void setup() {
		output = new StringWriter();
	}

	@Test
	void testEdit(@TempDir Path outputDir) throws IOException {

		String s = outputDir.resolve("edit.java").toString();
		Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));

		DecoratedSource xrunit = DecoratedSource.forResource(s);
		ScriptSource ssrc = (ScriptSource) xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		File project = new Edit().createProjectForEdit(ssrc, ctx, false);

		assertThat(new File(project, "src"), FileMatchers.anExistingDirectory());
		File build = new File(project, "build.gradle");
		assert (build.exists());
		MatcherAssert.assertThat(Util.readString(build.toPath()), containsString("dependencies"));
		File src = new File(project, "src/edit.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(src.toPath()) || src.exists());

		// check eclipse is there
		assertThat(Arrays.stream(project.listFiles()).map(File::getName).collect(Collectors.toList()),
				containsInAnyOrder(".project", ".classpath", ".eclipse", "src", "build.gradle", ".vscode",
						"README.md"));
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
		Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));

		Util.writeString(p, "//DEPS org.openjfx:javafx-graphics:11.0.2${bougus:}\n" + Util.readString(p));

		DecoratedSource xrunit = DecoratedSource.forResource(s);
		ScriptSource src = (ScriptSource) xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		File project = new Edit().createProjectForEdit(src, ctx, false);

		File gradle = new File(project, "build.gradle");
		assert (gradle.exists());
		String buildGradle = Util.readString(gradle.toPath());
		assertThat(buildGradle, not(containsString("bogus")));
		assertThat(buildGradle, containsString("id 'application'"));
		assertThat(buildGradle, containsString("mainClass = 'edit'"));

		File java = new File(project, "src/edit.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(java.toPath()) || java.exists());

		assertThat(Files.isSameFile(java.toPath(), p), equalTo(true));
	}

	@Test
	void testEditJitPackDepAndRepo(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("edit.java");
		String s = p.toString();
		Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));

		Util.writeString(p, "//DEPS https://github.com/oldskoolsh/libvirt-schema/tree/0.0.2\n" + Util.readString(p));

		DecoratedSource xrunit = DecoratedSource.forResource(s);
		ScriptSource src = (ScriptSource) xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		File project = new Edit().createProjectForEdit(src, ctx, false);

		File gradle = new File(project, "build.gradle");
		assert (gradle.exists());
		String buildGradle = Util.readString(gradle.toPath());
		assertThat(buildGradle, not(containsString("github.com"))); // should be com.github
		assertThat(buildGradle, containsString("jcenter")); // auto-added jcenter
		assertThat(buildGradle, containsString("jitpack.io")); // auto-added jitpack repo
		assertThat(buildGradle, containsString("compile \"com.github.oldskoolsh:libvirt-schema:0.0.2\""));
	}

	@Test
	void testEditMultiSource(@TempDir Path outputDir) throws IOException {

		Path p = examplesTestFolder.toPath().resolve("one.java");
		assertThat(p.toFile().exists(), is(true));

		DecoratedSource xrunit = DecoratedSource.forResource(p.toString());
		ScriptSource src = (ScriptSource) xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		File project = new Edit().createProjectForEdit(src, ctx, false);

		File gradle = new File(project, "build.gradle");
		assert (gradle.exists());
		assertThat(Util.readString(gradle.toPath()), not(containsString("bogus")));

		Arrays	.asList("one.java", "Two.java", "gh_fetch_release_assets.java", "gh_release_stats.java")
				.forEach(f -> {
					File java = new File(project, "src/" + f);

					assertThat(f + " not found", java, aReadableFile());
				});
	}

	@Test
	void testEditNonJava(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("kube-example");
		String s = p.toString();
		Jbang.getCommandLine().execute("init", s);
		assertThat(new File(s).exists(), is(true));

		DecoratedSource xrunit = DecoratedSource.forResource(s);
		ScriptSource src = (ScriptSource) xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		File project = new Edit().createProjectForEdit(src, ctx, false);

		File java = new File(project, "src/KubeExample.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(java.toPath()) || java.exists());
	}

}
