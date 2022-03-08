package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.source.builders.BaseBuilder.escapeOSArguments;
import static dev.jbang.util.Util.verboseMsg;
import static java.lang.System.out;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.net.EditorManager;
import dev.jbang.source.*;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;
import dev.jbang.util.Util.Shell;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Setup a temporary project to edit script in an IDE.")
public class Edit extends BaseScriptCommand {

	// static String[] knownEditors = { "code", "eclipse", "idea", "netbeans", "vi",
	// "emacs" };
	static String[] knownEditors = { "code", "eclipse", "idea", "netbeans" };

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Option(names = { "-s", "--sources" }, description = "Add additional sources.")
	List<String> sources;

	@CommandLine.Option(names = {
			"--live" }, description = "Setup temporary project, regenerate project on dependency changes.")
	public boolean live;

	@CommandLine.Option(names = {
			"--open" }, arity = "0..1", defaultValue = "${JBANG_EDITOR:-${jbang.edit.open:-}}", fallbackValue = "${JBANG_EDITOR:-${jbang.edit.open:-}}", description = "Opens editor/IDE on the temporary project.", preprocessor = StrictParameterPreprocessor.class)
	public Optional<String> editor;

	@CommandLine.Option(names = { "--no-open" })
	public boolean noOpen;

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = getRunContext();
		Code code = ctx.forResource(scriptOrFile);

		if (!(code instanceof SourceSet)) {
			throw new ExitException(EXIT_INVALID_INPUT, "You can only edit source files");
		}

		SourceSet ss = (SourceSet) code;
		File project = createProjectForEdit(ss, ctx, false);
		String projectPathString = Util.pathToString(project.getAbsoluteFile().toPath());
		// err.println(project.getAbsolutePath());

		if (!noOpen) {
			if (!editor.isPresent() || editor.get().isEmpty()) {
				editor = askEditor();
				if (!editor.isPresent()) {
					return EXIT_OK;
				}
			} else {
				showStartingMsg(editor.get(), !editor.get().equals(spec.findOption("open").defaultValue()));
			}
			if ("gitpod".equals(editor.get()) && System.getenv("GITPOD_WORKSPACE_URL") != null) {
				info("Open this url to edit the project in your gitpod session:\n\n"
						+ System.getenv("GITPOD_WORKSPACE_URL") + "#" + project.getAbsolutePath() + "\n\n");
			} else {
				List<String> optionList = new ArrayList<>();
				optionList.add(editor.get());
				optionList.add(projectPathString);

				String[] cmd;
				if (Util.getShell() == Shell.bash) {
					final String editorCommand = String.join(" ", escapeOSArguments(optionList, Shell.bash));
					cmd = new String[] { "sh", "-c", editorCommand };
				} else {
					final String editorCommand = String.join(" ", escapeOSArguments(optionList, Shell.cmd));
					cmd = new String[] { "cmd", "/c", editorCommand };
				}
				verboseMsg("Running `" + String.join(" ", cmd) + "`");
				new ProcessBuilder(cmd).start();
			}
		}

		if (!live) {
			out.println(projectPathString); // quit(project.getAbsolutePath());
		} else {
			try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
				File orginalFile = code.getResourceRef().getFile();
				if (!orginalFile.exists()) {
					throw new ExitException(EXIT_UNEXPECTED_STATE,
							"Cannot live edit " + code.getResourceRef().getOriginalResource());
				}
				Path watched = orginalFile.getAbsoluteFile().getParentFile().toPath();
				watched.register(watchService,
						StandardWatchEventKinds.ENTRY_MODIFY);
				info("Watching for changes in " + watched);
				while (true) {
					final WatchKey wk = watchService.take();
					for (WatchEvent<?> event : wk.pollEvents()) {
						// we only register "ENTRY_MODIFY" so the context is always a Path.
						// but relative to the watched directory
						final Path changed = watched.resolve((Path) event.context());
						verboseMsg("Changed file: " + changed.toString());
						if (Files.isSameFile(orginalFile.toPath(), changed)) {
							try {
								// TODO only regenerate when dependencies changes.
								info("Regenerating project.");
								ctx = RunContext.empty();
								code = ctx.forResource(scriptOrFile);
								ss = (SourceSet) code;
								createProjectForEdit(ss, ctx, true);
							} catch (RuntimeException ee) {
								warn("Error when re-generating project. Ignoring it, but state might be undefined: "
										+ ee.getMessage());
							}
						}
					}
					// reset the key
					boolean valid = wk.reset();
					if (!valid) {
						warn("edit-live file watch key no longer valid!");
					}
				}
			} catch (InterruptedException e) {
				warn("edit-live interrupted");
			}
		}
		return EXIT_OK;
	}

	RunContext getRunContext() {
		RunContext ctx = new RunContext();
		ctx.setProperties(dependencyInfoMixin.getProperties());
		ctx.setAdditionalDependencies(dependencyInfoMixin.getDependencies());
		ctx.setAdditionalRepositories(dependencyInfoMixin.getRepositories());
		ctx.setAdditionalClasspaths(dependencyInfoMixin.getClasspaths());
		ctx.setAdditionalSources(sources);
		ctx.setForceJsh(forcejsh);
		return ctx;
	}

	private static Optional<String> askEditor() throws IOException {
		Path editorBinPath = EditorManager.getVSCodiumBinPath();
		Path dataPath = EditorManager.getVSCodiumDataPath();
		Path editorPath = EditorManager.getVSCodiumPath();

		if (!Files.exists(editorBinPath)) {
			String question = "You requested to open default editor but no default editor configured.\n" +
					"\n" +
					"jbang can download and configure a visual studio code (VSCodium) with Java support to use\n" +
					"See https://vscodium.com for details\n" +
					"\n" +
					"Do you want to";

			List<String> options = new ArrayList<>();
			options.add("Download and run VSCodium");

			List<String> pathEditors = findEditorsOnPath();
			for (String ed : pathEditors) {
				options.add("Use '" + ed + "'");
			}

			int result = Util.askInput(question, 30, 0, options.toArray(new String[] {}));
			if (result == 0) {
				return Optional.empty();
			} else if (result == 1) {
				setupEditor(editorBinPath, dataPath);
			} else if (result > 1) {
				String ed = pathEditors.get(result - 2);
				showStartingMsg(ed, true);
				return Optional.of(ed);
			} else {
				throw new ExitException(EXIT_GENERIC_ERROR,
						"No default editor configured and no other option accepted.\n Please try again making a correct choice or use an explicit editor, i.e. `jbang edit --open=eclipse xyz.java`");
			}
		}

		return Optional.of(editorBinPath.toAbsolutePath().toString());
	}

	private static void setupEditor(Path editorBinPath, Path dataPath) throws IOException {
		EditorManager.downloadAndInstallEditor();

		if (!Files.exists(dataPath)) {
			verboseMsg("Making portable data path " + dataPath.toString());
			Files.createDirectories(dataPath);
		}

		verboseMsg("Installing Java extensions...");
		ProcessBuilder pb = new ProcessBuilder(editorBinPath.toAbsolutePath().toString(),
				"--install-extension", "redhat.java",
				"--install-extension", "vscjava.vscode-java-debug",
				"--install-extension", "vscjava.vscode-java-test",
				"--install-extension", "vscjava.vscode-java-dependency");
		pb.inheritIO();
		Process process = pb.start();
		try {
			int exit = process.waitFor();
			if (exit > 0) {
				throw new ExitException(EXIT_INTERNAL_ERROR,
						"Could not install and setup extensions into VSCodium. Aborting.");
			}
		} catch (InterruptedException e) {
			Util.errorMsg("Problems installing VSCodium extensions", e);
		}
	}

	private static List<String> findEditorsOnPath() {
		return Arrays.stream(knownEditors).filter(e -> Util.searchPath(e) != null).collect(Collectors.toList());
	}

	private static void showStartingMsg(String ed, boolean showConfig) {
		String msg = "Starting '" + ed + "'.";
		if (showConfig) {
			msg += "If you want to make this the default, run 'jbang config set edit.open " + ed + "'";
		}
		Util.infoMsg(msg);
	}

	/** Create Project to use for editing **/
	File createProjectForEdit(SourceSet ss, RunContext ctx, boolean reload) throws IOException {
		File originalFile = ss.getResourceRef().getFile();

		List<String> dependencies = ss.getDependencies();
		String cp = ss.getClassPath().getClassPath();
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		File baseDir = Settings.getCacheDir(Cache.CacheClass.projects).toFile();

		String name = originalFile.getName();
		name = Util.unkebabify(name);

		File tmpProjectDir = new File(baseDir, name + "_jbang_" +
				Util.getStableID(originalFile.getAbsolutePath()));
		tmpProjectDir.mkdirs();
		tmpProjectDir = new File(tmpProjectDir, stripPrefix(name));
		tmpProjectDir.mkdirs();

		File srcDir = new File(tmpProjectDir, "src");
		srcDir.mkdir();

		Path srcFile = srcDir.toPath().resolve(name);
		Util.createLink(srcFile, originalFile.toPath());

		for (Source source : ss.getSources()) {
			File sfile = null;
			if (source.getJavaPackage().isPresent()) {
				File packageDir = new File(srcDir, source.getJavaPackage().get().replace(".", File.separator));
				packageDir.mkdirs();
				sfile = new File(packageDir, source.getResourceRef().getFile().getName());
			} else {
				sfile = new File(srcDir, source.getResourceRef().getFile().getName());
			}
			Path destFile = source.getResourceRef().getFile().toPath().toAbsolutePath();
			Util.createLink(sfile.toPath(), destFile);
		}

		for (RefTarget ref : ss.getResources()) {
			File target = ref.to(srcDir.toPath()).toFile();
			target.getParentFile().mkdirs();
			Util.createLink(target.toPath(), ref.getSource().getFile().toPath().toAbsolutePath());
		}

		// create build gradle
		Optional<String> packageName = Util.getSourcePackage(
				new String(Files.readAllBytes(srcFile), Charset.defaultCharset()));
		String baseName = Util.getBaseName(name);
		String fullClassName;
		fullClassName = packageName.map(s -> s + "." + baseName).orElse(baseName);
		String templateName = "build.qute.gradle";
		Path destination = new File(tmpProjectDir, "build.gradle").toPath();
		TemplateEngine engine = TemplateEngine.instance();

		// both collectDependencies and repositories are manipulated by
		// resolveDependencies
		List<MavenRepo> repositories = ss.getRepositories();
		if (repositories.isEmpty()) {
			ss.addRepository(DependencyUtil.toMavenRepo("mavencentral"));
		}

		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = dependencies
											.stream()
											.map(JitPackUtil::ensureGAV)
											.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(dependencies)
				&& repositories.stream().noneMatch(r -> DependencyUtil.REPO_JITPACK.equals(r.getUrl()))) {
			ss.addRepository(DependencyUtil.toMavenRepo(DependencyUtil.ALIAS_JITPACK));
		}

		renderTemplate(engine, depIds, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				ctx.getArguments(),
				destination);

		// setup eclipse
		templateName = ".qute.classpath";
		destination = new File(tmpProjectDir, ".classpath").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				ctx.getArguments(),
				destination);

		templateName = ".qute.project";
		destination = new File(tmpProjectDir, ".project").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				ctx.getArguments(),
				destination);

		templateName = "main.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + ".launch").toPath();
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				ctx.getArguments(),
				destination);

		templateName = "main-port-4004.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + "-port-4004.launch").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				ctx.getArguments(),
				destination);

		// setup vscode
		templateName = "launch.qute.json";
		destination = new File(tmpProjectDir, ".vscode/launch.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					ctx.getArguments(),
					destination);
		}

		// setup vscode
		templateName = "README.qute.md";
		destination = new File(tmpProjectDir, "README.md").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					ctx.getArguments(),
					destination);
		}

		templateName = "settings.qute.json";
		destination = new File(tmpProjectDir, ".vscode/settings.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					ctx.getArguments(),
					destination);
		}

		// setup intellij - disabled for now as idea was not picking these up directly
		/*
		 * templateName = "idea-port-4004.qute.xml"; destination = new
		 * File(tmpProjectDir, ".idea/runConfigurations/" + baseName +
		 * "-port-4004.xml").toPath(); destination.toFile().getParentFile().mkdirs();
		 * renderTemplate(engine, collectDependencies, baseName, resolvedDependencies,
		 * templateName, script.getArguments(), destination);
		 *
		 * templateName = "idea.qute.xml"; destination = new File(tmpProjectDir,
		 * ".idea/runConfigurations/" + baseName + ".xml").toPath();
		 * destination.toFile().getParentFile().mkdirs(); renderTemplate(engine,
		 * collectDependencies, baseName, resolvedDependencies, templateName,
		 * script.getArguments(), destination);
		 */

		return tmpProjectDir;
	}

	private boolean isNeeded(boolean reload, Path file) {
		return !file.toFile().exists() && !reload;
	}

	private void renderTemplate(TemplateEngine engine, List<String> collectDependencies, String fullclassName,
			String baseName,
			List<String> resolvedDependencies, List<MavenRepo> repositories, String templateName,
			List<String> userParams, Path destination)
			throws IOException {
		Template template = engine.getTemplate(templateName);
		if (template == null)
			throw new ExitException(EXIT_INVALID_INPUT, "Could not locate template named: '" + templateName + "'");
		String result = template
								.data("repositories",
										repositories.stream()
													.map(MavenRepo::getUrl)
													.filter(s -> !"".equals(s)))
								.data("dependencies", collectDependencies)
								.data("gradledependencies", gradleify(collectDependencies))
								.data("baseName", baseName)
								.data("fullClassName", fullclassName)
								.data("classpath",
										resolvedDependencies.stream()
															.filter(t -> !t.isEmpty())
															.collect(Collectors.toList()))
								.data("userParams", String.join(" ", userParams))
								.data("cwd", System.getProperty("user.dir"))
								.render();

		Util.writeString(destination, result);
	}

	private List<String> gradleify(List<String> collectDependencies) {
		return collectDependencies.stream().map(item -> {
			if (item.endsWith("@pom")) {
				return "implementation platform ('" + item.substring(0, item.lastIndexOf("@pom")) + "')";
			} else {
				return "implementation '" + item + "'";
			}
		}).collect(Collectors.toList());
	}

	static String stripPrefix(String fileName) {
		if (fileName.indexOf(".") > 0) {
			return fileName.substring(0, fileName.lastIndexOf("."));
		} else {
			return fileName;
		}
	}

}
