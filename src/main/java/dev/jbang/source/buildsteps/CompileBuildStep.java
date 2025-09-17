package dev.jbang.source.buildsteps;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
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

	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);

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
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
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

				Indexer indexer = new Indexer();
				Index index;
				for (Path item : items) {
					try (InputStream stream = new FileInputStream(item.toFile())) {
						indexer.index(stream);
					}
				}
				index = indexer.complete();

				Collection<ClassInfo> classes = index.getKnownClasses();

				Project project = ctx.getProject();
				if (project.getMainClass() == null) { // if non-null user forced set main
					List<ClassInfo> mains = classes.stream()
						.filter(getMainFinder())
						.collect(Collectors.toList());
					String mainName = getSuggestedMain();
					if (mains.size() > 1 && mainName != null) {
						List<ClassInfo> suggestedmain = mains.stream()
							.filter(ci -> ci.simpleName().equals(mainName))
							.collect(Collectors.toList());
						if (!suggestedmain.isEmpty()) {
							mains = suggestedmain;
						}
					}

					if (!mains.isEmpty()) {
						project.setMainClass(mains.get(0).name().toString());
						if (mains.size() > 1) {
							Util.warnMsg(
									"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
											+ mains.stream()
												.map(x -> x.name().toString())
												.collect(Collectors.joining(",")));
						}
					}
				}

				if (project.getMainSource().isAgent()) {
					Optional<ClassInfo> agentmain = classes.stream()
						.filter(pubClass -> pubClass.method("agentmain",
								STRINGTYPE,
								INSTRUMENTATIONTYPE) != null
								||
								pubClass.method("agentmain",
										STRINGTYPE) != null)
						.findFirst();

					if (agentmain.isPresent()) {
						project.setAgentMainClass(agentmain.get().name().toString());
					}

					Optional<ClassInfo> premain = classes.stream()
						.filter(pubClass -> pubClass.method("premain",
								STRINGTYPE,
								INSTRUMENTATIONTYPE) != null
								||
								pubClass.method("premain",
										STRINGTYPE) != null)
						.findFirst();

					if (premain.isPresent()) {
						project.setPreMainClass(premain.get().name().toString());
					}
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

	protected Predicate<ClassInfo> getMainFinder() {
		return pubClass -> (pubClass.method("main", STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null);
	}
}
