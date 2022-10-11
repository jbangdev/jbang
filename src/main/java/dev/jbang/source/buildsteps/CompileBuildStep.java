package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

/**
 * This class takes a <code>Project</code> and compiles it.
 */
public abstract class CompileBuildStep implements Builder<Project> {
	protected final Project project;

	public CompileBuildStep(Project project) {
		this.project = project;
	}

	@Override
	public Project build() throws IOException {
		return compile();
	}

	protected Project compile() throws IOException {
		String requestedJavaVersion = project.getJavaVersion();
		Path compileDir = project.getBuildDir();
		List<String> optionList = new ArrayList<>();
		optionList.add(getCompilerBinary(requestedJavaVersion));
		optionList.add("-g");
		optionList.addAll(project.getMainSourceSet().getCompileOptions());
		String path = project.resolveClassPath().getClassPath();
		if (!Util.isBlankString(path)) {
			optionList.addAll(Arrays.asList("-classpath", path));
		}
		optionList.addAll(Arrays.asList("-d", compileDir.toAbsolutePath().toString()));

		// add source files to compile
		optionList.addAll(project	.getMainSourceSet()
									.getSources()
									.stream()
									.map(x -> x.getFile().toString())
									.collect(Collectors.toList()));

		// add additional files
		project.getMainSourceSet().copyResourcesTo(compileDir);

		generatePom();

		Util.infoMsg(String.format("Building %s...", project.getMainSource().isAgent() ? "javaagent" : "jar"));
		Util.verboseMsg("Compile: " + String.join(" ", optionList));
		runCompiler(optionList);

		return project;
	}

	protected void runCompiler(List<String> optionList) throws IOException {
		runCompiler(CommandBuffer.of(optionList).asProcessBuilder().inheritIO());
	}

	protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
		Process process = processBuilder.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during compile");
		}
	}

	protected Path generatePom() throws IOException {
		Template pomTemplate = TemplateEngine.instance().getTemplate("pom.qute.xml");

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			String baseName = Util.getBaseName(project.getResourceRef().getFile().getFileName().toString());
			MavenCoordinate gav = getPomGav(project);
			String pomfile = pomTemplate
										.data("baseName", baseName)
										.data("group", gav.getGroupId())
										.data("artifact", gav.getArtifactId())
										.data("version", gav.getVersion())
										.data("description", project.getDescription().orElse(""))
										.data("dependencies", project.resolveClassPath().getArtifacts())
										.render();

			pomPath = getPomPath(project);
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}

		return pomPath;
	}

	public static MavenCoordinate getPomGav(Project prj) {
		if (prj.getGav().isPresent()) {
			return MavenCoordinate.fromString(prj.getGav().get()).withVersion();
		} else {
			String baseName = Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
			return new MavenCoordinate(MavenCoordinate.DUMMY_GROUP, baseName, MavenCoordinate.DEFAULT_VERSION);
		}

	}

	public static Path getPomPath(Project prj) {
		MavenCoordinate gav = getPomGav(prj);
		return prj.getBuildDir().resolve("META-INF/maven/" + gav.getGroupId().replace(".", "/") + "/pom.xml");
	}

	protected abstract String getCompilerBinary(String requestedJavaVersion);
}
