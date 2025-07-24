package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
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

import dev.jbang.*;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestEdit extends BaseTest {

	StringWriter output;

	@BeforeEach
	void setup() {
		output = new StringWriter();
	}

	@Test
	void testEdit(@TempDir Path outputDir) throws IOException {

		String s = outputDir.resolve("edit.java").toString();
		JBang.getCommandLine().execute("init", s);
		org.assertj.core.api.Assertions.assertThat(new File(s).exists()).isEqualTo(true);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(s);

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		assertThat(project.resolve("src").toFile(), FileMatchers.anExistingDirectory());
		Path build = project.resolve("build.gradle");
		assert (Files.exists(build));
		org.assertj.core.api.Assertions.assertThat(Util.readString(build)).contains("dependencies");
		Path src = project.resolve("src/edit.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(src) || Files.exists(src));

		// check eclipse is there
		org.assertj.core.api.Assertions.assertThat(Files.list(project).map(p -> p.getFileName().toString()).collect(Collectors.toList())).containsExactlyInAnyOrder(".project", ".classpath", ".eclipse", "src", "build.gradle", ".vscode", "README.md");
		Path launchfile = project.resolve(".eclipse/edit.launch");
		assert (Files.exists(launchfile));
		org.assertj.core.api.Assertions.assertThat(Util.readString(launchfile)).contains("launching.PROJECT_ATTR");
		org.assertj.core.api.Assertions.assertThat(Util.readString(launchfile)).contains("PROGRAM_ARGUMENTS\" value=\"\"");

		launchfile = project.resolve(".eclipse/edit-port-4004.launch");
		assert (Files.exists(launchfile));
		org.assertj.core.api.Assertions.assertThat(Util.readString(launchfile)).contains("launching.PROJECT_ATTR");
		org.assertj.core.api.Assertions.assertThat(Util.readString(launchfile)).contains("4004");
	}

	@Test
	void testEditDeps(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("edit.java");
		String s = p.toString();
		JBang.getCommandLine().execute("--verbose", "init", s);
		org.assertj.core.api.Assertions.assertThat(new File(s).exists()).isEqualTo(true);

		Util.writeString(p, "//DEPS org.openjfx:javafx-graphics:11.0.2${bougus:}\n" + Util.readString(p));

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(s);

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path gradle = project.resolve("build.gradle");
		assert (Files.exists(gradle));
		String buildGradle = Util.readString(gradle);
		org.assertj.core.api.Assertions.assertThat(buildGradle).doesNotContain("bogus");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("id 'application'");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("mainClass = 'edit'");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("repo1.maven.org"); // auto-added maven
		org.assertj.core.api.Assertions.assertThat(buildGradle).doesNotContain("jitpack.io"); // auto-added jitpack repo

		Path java = project.resolve("src/edit.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(java) || Files.exists(java));

		org.assertj.core.api.Assertions.assertThat(Files.isSameFile(java, p)).isEqualTo(true);
	}

	@Test
	void testEditDepsNoJitpack(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("edit.java");
		String s = p.toString();
		JBang.getCommandLine().execute("init", s);
		org.assertj.core.api.Assertions.assertThat(new File(s).exists()).isEqualTo(true);

		Util.writeString(p, "//DEPS io.quarkus:quarkus-bom:2.0.0.Final@pom\n" +
				"//DEPS io.quarkus:quarkus-rest-client-reactive\n" +
				"//DEPS io.quarkus:quarkus-rest-client-reactive-jackson\n" + Util.readString(p));

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(s);

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path gradle = project.resolve("build.gradle");
		assert (Files.exists(gradle));
		String buildGradle = Util.readString(gradle);
		org.assertj.core.api.Assertions.assertThat(buildGradle).doesNotContain("bogus");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("id 'application'");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("mainClass = 'edit'");
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("repo1.maven.org"); // auto-added maven
		org.assertj.core.api.Assertions.assertThat(buildGradle).doesNotContain("jitpack.io"); // auto-added jitpack repo

		Path java = project.resolve("src/edit.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(java) || Files.exists(java));

		org.assertj.core.api.Assertions.assertThat(Files.isSameFile(java, p)).isEqualTo(true);
	}

	@Test
	void testEditJitPackDepAndRepo(@TempDir Path outputDir) throws IOException {

		Path p = outputDir.resolve("edit.java");
		String s = p.toString();
		JBang.getCommandLine().execute("init", s);
		org.assertj.core.api.Assertions.assertThat(new File(s).exists()).isEqualTo(true);

		Util.writeString(p, "//DEPS https://github.com/oldskoolsh/libvirt-schema/tree/0.0.2\n" + Util.readString(p));

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(s);

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path gradle = project.resolve("build.gradle");
		assert (Files.exists(gradle));
		String buildGradle = Util.readString(gradle);
		org.assertj.core.api.Assertions.assertThat(buildGradle).doesNotContain("github.com"); // should be com.github
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("repo1.maven.org"); // auto-added maven
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("jitpack.io"); // auto-added jitpack repo
		org.assertj.core.api.Assertions.assertThat(buildGradle).contains("implementation 'com.github.oldskoolsh:libvirt-schema:0.0.2'");
	}

	@Test
	void testEditMultiSource(@TempDir Path outputDir) throws IOException {

		Path p = examplesTestFolder.resolve("one.java");
		org.assertj.core.api.Assertions.assertThat(p.toFile().exists()).isEqualTo(true);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(p.toString());

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path gradle = project.resolve("build.gradle");
		assert (Files.exists(gradle));
		org.assertj.core.api.Assertions.assertThat(Util.readString(gradle)).doesNotContain("bogus");

		Arrays.asList("one.java", "Two.java", "gh_fetch_release_assets.java", "gh_release_stats.java")
			.forEach(f -> {
				Path java = project.resolve("src/" + f);

				assertThat(f + " not found", java.toFile(), aReadableFile());
			});
	}

	@Test
	void testEditNonJava(@TempDir Path outputDir) throws IOException {
		Path p = outputDir.resolve("kube-example");
		int result = JBang.getCommandLine().execute("init", p.toString());
		String s = p.toString();
		org.assertj.core.api.Assertions.assertThat(result).isEqualTo(0);
		org.assertj.core.api.Assertions.assertThat(new File(s).exists()).isEqualTo(true);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(s);

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path java = project.resolve("src/KubeExample.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assert (Files.isSymbolicLink(java) || Files.exists(java));
	}

	@Test
	void testEditFile(@TempDir Path outputDir) throws IOException {

		Path p = examplesTestFolder.resolve("res/resource.java");
		org.assertj.core.api.Assertions.assertThat(p.toFile().exists()).isEqualTo(true);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(p.toString());

		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);

		Path gradle = project.resolve("build.gradle");
		assert (Files.exists(gradle));
		org.assertj.core.api.Assertions.assertThat(Util.readString(gradle)).doesNotContain("bogus");

		Arrays.asList("resource.java", "resource.properties", "renamed.properties", "META-INF/application.properties")
			.forEach(f -> {
				Path java = project.resolve("src/" + f);

				assertThat(f + " not found", java.toFile(), aReadableFile());
			});
	}

	/*
	 * @Test void testEditMissingScript() {
	 * assertThrows(IllegalArgumentException.class, () -> { CommandLine.ParseResult
	 * pr = JBang.getCommandLine().parseArgs("edit"); Edit edit = (Edit)
	 * pr.subcommand().commandSpec().userObject(); edit.doCall(); }); }
	 */

	@Test
	void testSandboxEditNonSource() {
		assertThrows(ExitException.class, () -> {
			Path jar = examplesTestFolder.resolve("hellojar.jar");
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "-b", "--no-open", jar.toString());
			Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
			edit.doCall();
		});
	}

	@Test
	void testSandboxEdit() throws IOException {
		Path src = examplesTestFolder.resolve("helloworld.java");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "-b", "--no-open", src.toString());
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		edit.doCall();
	}
}
