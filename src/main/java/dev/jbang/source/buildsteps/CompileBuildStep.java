package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

/**
 * This class takes a <code>Project</code> and compiles it.
 */
public abstract class CompileBuildStep implements Builder<Project> {
	protected final Project project;
	protected final BuildContext ctx;

	public CompileBuildStep(Project project, BuildContext ctx) {
		this.project = project;
		this.ctx = ctx;
	}

	@Override
	public Project build() throws IOException {
		return compile();
	}

	protected Project compile() throws IOException {
		String requestedJavaVersion = project.getJavaVersion();
		if (requestedJavaVersion == null
				&& project.getModuleName().isPresent()
				&& JavaUtil.javaVersion(null) < 9) {
			// Make sure we use at least Java 9 when dealing with modules
			requestedJavaVersion = "9+";
		}

		Path compileDir = ctx.getCompileDir();
		List<String> optionList = new ArrayList<>();
		optionList.add(getCompilerBinary(requestedJavaVersion));
		optionList.addAll(project.getMainSourceSet().getCompileOptions());
		String path = project.resolveClassPath().getClassPath();
		if (!Util.isBlankString(path)) {
			if (project.getModuleName().isPresent()) {
				optionList.addAll(Arrays.asList("-p", path));
			} else {
				optionList.addAll(Arrays.asList("-classpath", path));
			}
		}
		optionList.addAll(Arrays.asList("-d", compileDir.toAbsolutePath().toString()));

		// add source files to compile
		optionList.addAll(project	.getMainSourceSet()
									.getSources()
									.stream()
									.map(x -> x.getFile().toString())
									.collect(Collectors.toList()));

		if (project.getModuleName().isPresent() && !hasModuleInfoFile()) {
			// generate module-info descriptor and add it to list of files to compile
			Path infoFile = generateModuleInfo();
			if (infoFile != null) {
				optionList.add(infoFile.toString());
			}
		}

		// add additional files
		project.getMainSourceSet().copyResourcesTo(compileDir);

		generatePom();

		Util.infoMsg(String.format("Building %s...", project.getMainSource().isAgent() ? "javaagent" : "jar"));
		Util.verboseMsg("Compile: " + String.join(" ", optionList));
		runCompiler(optionList);

		return project;
	}

	private boolean hasModuleInfoFile() {
		return project	.getMainSourceSet()
						.getSources()
						.stream()
						.anyMatch(s -> s.getFile().getFileName().toString().equals("module-info.java"));
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

			pomPath = getPomPath(project, ctx);
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}

		return pomPath;
	}

	protected Path generateModuleInfo() throws IOException {
		Template infoTemplate = TemplateEngine.instance().getTemplate("module-info.qute.java");

		Path infoPath = null;
		if (infoTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate module-info.java template");
		} else {
			// First get the list of root dependencies as proper maven coordinates
			Set<MavenCoordinate> deps = project	.getMainSourceSet()
												.getDependencies()
												.stream()
												.map(MavenCoordinate::fromString)
												.collect(Collectors.toSet());
			// Now filter out the resolved artifacts that are root dependencies
			// and get their names
			List<String> moduleNames = project	.resolveClassPath()
												.getArtifacts()
												.stream()
												.filter(a -> deps.contains(a.getCoordinate()))
												.map(ArtifactInfo::getModuleName)
												.filter(Objects::nonNull)
												.collect(Collectors.toList());
			// Finally create a module-info file with the name of the module
			// and the list of required modules using the names we just listed
			String modName = project.getModuleName().orElse(project.getGav().orElse("jbangapp"));
			String infoFile = infoTemplate
											.data("name", modName)
											.data("dependencies", moduleNames)
											.render();

			infoPath = ctx.getGeneratedSourcesDir().resolve("module-info.java");
			Files.createDirectories(infoPath.getParent());
			Util.writeString(infoPath, infoFile);
		}

		return infoPath;
	}

	public static MavenCoordinate getPomGav(Project prj) {
		if (prj.getGav().isPresent()) {
			return MavenCoordinate.fromString(prj.getGav().get()).withVersion();
		} else {
			String baseName = Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
			return new MavenCoordinate(MavenCoordinate.DUMMY_GROUP, baseName, MavenCoordinate.DEFAULT_VERSION);
		}

	}

	public static Path getPomPath(Project prj, BuildContext ctx) {
		MavenCoordinate gav = getPomGav(prj);
		return ctx.getCompileDir().resolve("META-INF/maven/" + gav.getGroupId().replace(".", "/") + "/pom.xml");
	}

	protected abstract String getCompilerBinary(String requestedJavaVersion);
}
