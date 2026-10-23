package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.ExitException;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.MainClassScanner;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

/**
 * This class takes a <code>Project</code> and compiles it.
 */
public abstract class CompileBuildStep implements Builder<Project> {
	protected final BuildContext ctx;

	public CompileBuildStep(BuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public Project build() throws IOException {
		return compile();
	}

	protected Project compile() throws IOException {
		List<String> compileCmd = getCompileCommand();

		// add additional files
		Project project = ctx.getProject();
		project.getMainSourceSet().copyResourcesTo(ctx.getCompileDir());

		generatePom();

		Util.infoMsg(String.format("Building %s for %s...", project.getMainSource().isAgent() ? "javaagent" : "jar",
				project.getResourceRef().getFile().getFileName().toString()));
		Util.verboseMsg("Compile: " + String.join(" ", compileCmd));
		runCompiler(compileCmd);

		searchForMain(ctx.getCompileDir());

		return project;
	}

	protected List<String> getCompileCommand() throws IOException {
		List<String> compileCmd = new ArrayList<>();

		Project project = ctx.getProject();
		compileCmd.add(getCompilerBinary());
		compileCmd.addAll(getCompileCommandOptions());

		// add source files to compile
		compileCmd.addAll(project.getMainSourceSet()
			.getSources()
			.stream()
			.map(x -> x.getFile().toString())
			.collect(Collectors.toList()));

		if (project.getModuleName().isPresent()) {
			if (project.getMainSource() != null && !project.getMainSource().getJavaPackage().isPresent()) {
				throw new ExitException(ExitException.EXIT_INVALID_INPUT,
						"Module code cannot work with the default package, adding a 'package' statement is required");
			}
			if (!hasModuleInfoFile()) {
				// generate module-info descriptor and add it to list of files to compile
				Path infoFile = ModuleUtil.generateModuleInfo(ctx);
				if (infoFile != null) {
					compileCmd.add(infoFile.toString());
				}
			}
		}

		return compileCmd;
	}

	private boolean hasModuleInfoFile() {
		return ctx.getProject()
			.getMainSourceSet()
			.getSources()
			.stream()
			.anyMatch(s -> s.getFile().getFileName().toString().equals("module-info.java"));
	}

	protected abstract String getCompilerBinary();

	protected abstract List<String> getCompileCommandOptions() throws IOException;

	protected void runCompiler(List<String> optionList) throws IOException {
		runCompiler(CommandBuffer.of(optionList)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder()
			.inheritIO());
	}

	protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
		Process process = Util.run(processBuilder);
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
		Template pomTemplate = TemplateEngine.instance()
			.getTemplate(ResourceRef.forResource("classpath:/pom.qute.xml"));

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			Project project = ctx.getProject();
			String baseName = Util.getBaseName(project.getResourceRef().getFile().getFileName().toString());
			MavenCoordinate gav = getPomGav(project);
			String pomfile = pomTemplate
				.data("baseName", baseName)
				.data("group", gav.getGroupId())
				.data("artifact", gav.getArtifactId())
				.data("version", gav.getVersion())
				.data("description", project.getDescription().orElse(""))
				.data("dependencies", ctx.resolveClassPath().getArtifacts())
				.render();

			pomPath = getPomPath(ctx);
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}

		return pomPath;
	}

	private static MavenCoordinate getPomGav(Project prj) {
		if (prj.getGav().isPresent()) {
			return MavenCoordinate.fromString(prj.getGav().get()).withVersion();
		} else {
			String baseName = Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
			return new MavenCoordinate(MavenCoordinate.DUMMY_GROUP, baseName, MavenCoordinate.DEFAULT_VERSION);
		}

	}

	public static Path getPomPath(BuildContext ctx) {
		MavenCoordinate gav = getPomGav(ctx.getProject());
		return ctx.getCompileDir()
			.resolve("META-INF/maven/" + gav.getGroupId().replace(".", "/") + "/pom.xml");
	}

	protected void searchForMain(Path tmpJarDir) {
		try {
			// using Files.walk method with try-with-resources
			try (Stream<Path> paths = Files.walk(tmpJarDir)) {
				List<Path> items = paths.filter(Files::isRegularFile)
					.filter(f -> !f.toFile().getName().contains("$"))
					.filter(f -> f.toFile().getName().endsWith(".class"))
					.collect(Collectors.toList());

				MainClassScanner.MainScan scan = MainClassScanner.scan(consumer -> {
					for (Path item : items) {
						try (InputStream stream = Files.newInputStream(item)) {
							consumer.accept(item.toString(), stream);
						}
					}
				});

				Project project = ctx.getProject();
				if (project.getMainClass() == null) { // if non-null user forced set main
					List<String> mains = scan.getMainClasses();
					String mainName = getSuggestedMain();
					if (mains.size() > 1 && mainName != null) {
						List<String> suggestedmain = mains.stream()
							.filter(n -> simpleName(n).equals(mainName))
							.collect(Collectors.toList());
						if (!suggestedmain.isEmpty()) {
							mains = suggestedmain;
						}
					}

					if (!mains.isEmpty()) {
						project.setMainClass(mains.get(0));
						if (mains.size() > 1) {
							Util.warnMsg(
									"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
											+ String.join(",", mains));
						}
					}
				}

				if (project.getMainSource().isAgent()) {
					scan.getAgentMain().ifPresent(project::setAgentMainClass);
					scan.getPreMain().ifPresent(project::setPreMainClass);
				}
			}
		} catch (IOException e) {
			throw new ExitException(1, e);
		}
	}

	protected String getSuggestedMain() {
		Project project = ctx.getProject();
		if (!project.getResourceRef().isStdin()) {
			return project.getResourceRef().getFile().getFileName().toString().replace("." + getMainExtension(), "");
		} else {
			return null;
		}
	}

	protected abstract String getMainExtension();

	private static String simpleName(String fqcn) {
		int i = fqcn.lastIndexOf('.');
		return i < 0 ? fqcn : fqcn.substring(i + 1);
	}
}
