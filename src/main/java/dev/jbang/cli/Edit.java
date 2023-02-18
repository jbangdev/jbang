package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;
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
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.net.EditorManager;
import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;
import dev.jbang.util.Util.Shell;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Setup a temporary project to edit script in an IDE.")
public class Edit extends BaseCommand {

	static String[] knownEditors = { "codium", "code", "eclipse", "idea", "netbeans" };

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

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
		scriptMixin.validate();

		// force download sources when editing
		Util.setDownloadSources(true);

		ProjectBuilder pb = createProjectBuilder();
		final Project prj = pb.build(scriptMixin.scriptOrFile);

		if (prj.isJar() || prj.getMainSourceSet().getSources().isEmpty()) {
			throw new ExitException(EXIT_INVALID_INPUT, "You can only edit source files");
		}

		Path project = createProjectForLinkedEdit(prj, Collections.emptyList(), false);
		String projectPathString = Util.pathToString(project.toAbsolutePath());
		// err.println(project.getAbsolutePath());

		if (!noOpen) {
			openEditor(project, projectPathString);
		}

		if (!live) {
			out.println(projectPathString); // quit(project.getAbsolutePath());
		} else {
			watchForChanges(prj, () -> {
				// TODO only regenerate when dependencies changes.
				info("Regenerating project.");
				try {
					createProjectForLinkedEdit(prj, Collections.emptyList(), true);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return null;
			});
		}
		return EXIT_OK;
	}

	private void watchForChanges(Project prj, Callable<Object> action) throws IOException {
		try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
			Path orginalFile = prj.getResourceRef().getFile();
			if (!Files.exists(orginalFile)) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Cannot live edit " + prj.getResourceRef().getOriginalResource());
			}
			Path watched = orginalFile.toAbsolutePath().getParent();
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
					if (Files.isSameFile(orginalFile, changed)) {
						try {
							action.call();
						} catch (RuntimeException ee) {
							warn("Error when re-generating project. Ignoring it, but state might be undefined: "
									+ ee.getMessage());
						} catch (IOException ioe) {
							throw ioe;
						} catch (Exception e) {
							throw new ExitException(EXIT_GENERIC_ERROR, "Exception when re-generating project. Exiting",
									e);
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

	// try open editor if possible and install if needed, returns true if editor
	// started, false if not possible (i.e. editor not available)
	private boolean openEditor(Path project, String projectPathString) throws IOException {
		if (!editor.isPresent() || editor.get().isEmpty()) {
			editor = askEditor();
			if (!editor.isPresent()) {
				return false;
			}
		} else {
			showStartingMsg(editor.get(), !editor.get().equals(spec.findOption("open").defaultValue()));
		}
		if ("gitpod".equals(editor.get()) && System.getenv("GITPOD_WORKSPACE_URL") != null) {
			info("Open this url to edit the project in your gitpod session:\n\n"
					+ System.getenv("GITPOD_WORKSPACE_URL") + "#" + project.toAbsolutePath() + "\n\n");
		} else {
			List<String> optionList = new ArrayList<>();
			optionList.add(editor.get());
			optionList.add(projectPathString);

			String[] cmd;
			if (Util.getShell() == Shell.bash) {
				final String editorCommand = CommandBuffer.of(optionList).asCommandLine(Shell.bash);
				cmd = new String[] { "sh", "-c", editorCommand };
			} else {
				final String editorCommand = CommandBuffer.of(optionList).asCommandLine(Shell.cmd);
				cmd = new String[] { "cmd", "/c", editorCommand };
			}
			verboseMsg("Running `" + String.join(" ", cmd) + "`");
			new ProcessBuilder(cmd).start();
		}
		return true;
	}

	ProjectBuilder createProjectBuilder() {
		return Project
						.builder()
						.setProperties(dependencyInfoMixin.getProperties())
						.additionalDependencies(dependencyInfoMixin.getDependencies())
						.additionalRepositories(dependencyInfoMixin.getRepositories())
						.additionalClasspaths(dependencyInfoMixin.getClasspaths())
						.additionalSources(scriptMixin.sources)
						.additionalResources(scriptMixin.resources)
						.forceType(scriptMixin.forceType);
	}

	private static Optional<String> askEditor() throws IOException {
		Path editorBinPath = EditorManager.getVSCodiumBinPath();
		Path dataPath = EditorManager.getVSCodiumDataPath();

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

		Path settingsjson = dataPath.resolve("user-data/User/settings.json");

		if (!Files.exists(settingsjson)) {
			verboseMsg("Setting up some good default settings at " + settingsjson);
			Files.createDirectories(settingsjson.getParent());

			String vscodeSettings = "{\n" +
			// better than breadcrumbs
					"    \"editor.experimental.stickyScroll.enabled\": true,\n" +
					// autosave because vscode has it default and it just makes things work smoother
					"    \"files.autoSave\": \"onFocusChange\",\n" +
					// use modern java
					"    \"java.codeGeneration.hashCodeEquals.useJava7Objects\": true,\n" +
					// instead of `out.println(x);` you get
					// `out.println(argClosestWithMatchingType)`
					"    \"java.completion.guessMethodArguments\": true,\n" +
					// when editing html/xml editing tags updates the matching pair
					"    \"editor.linkedEditing\": true,\n" +
					// looks cooler - doesn't hurt
					"    \"editor.cursorBlinking\": \"phase\",\n" +
					// making easy to zoom for presentations
					"    \"editor.mouseWheelZoom\": true\n" +
					"}";
			Util.writeString(settingsjson, vscodeSettings);
		}

		verboseMsg("Installing Java extensions...");
		ProcessBuilder pb = new ProcessBuilder(editorBinPath.toAbsolutePath().toString(),
				"--install-extension", "redhat.java",
				"--install-extension", "vscjava.vscode-java-debug",
				"--install-extension", "vscjava.vscode-java-test",
				"--install-extension", "vscjava.vscode-java-dependency",
				"--install-extension", "jbangdev.jbang-vscode");
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
	Path createProjectForLinkedEdit(Project prj, List<String> arguments, boolean reload) throws IOException {
		Path originalFile = prj.getResourceRef().getFile();

		List<String> dependencies = prj.getMainSourceSet().getDependencies();
		String cp = prj.resolveClassPath().getClassPath();
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		Path baseDir = Settings.getCacheDir(Cache.CacheClass.projects);

		String name = originalFile.getFileName().toString();
		name = Util.unkebabify(name);

		Path tmpProjectDir = baseDir.resolve(name + "_jbang_" +
				Util.getStableID(originalFile.toAbsolutePath().toString()));
		Util.mkdirs(tmpProjectDir);
		tmpProjectDir = tmpProjectDir.resolve(stripPrefix(name));
		Util.mkdirs(tmpProjectDir);

		Path srcDir = tmpProjectDir.resolve("src");
		Util.mkdirs(srcDir);

		Path srcFile = srcDir.resolve(name);
		Util.createLink(srcFile, originalFile);

		for (ResourceRef sourceRef : prj.getMainSourceSet().getSources()) {
			Path sfile;
			Source src = Source.forResourceRef(sourceRef, Function.identity());
			if (src.getJavaPackage().isPresent()) {
				Path packageDir = srcDir.resolve(src.getJavaPackage().get().replace(".", File.separator));
				Util.mkdirs(packageDir);
				sfile = packageDir.resolve(sourceRef.getFile().getFileName());
			} else {
				sfile = srcDir.resolve(sourceRef.getFile().getFileName());
			}
			Path destFile = sourceRef.getFile().toAbsolutePath();
			Util.createLink(sfile, destFile);
		}

		for (RefTarget ref : prj.getMainSourceSet().getResources()) {
			Path target = ref.to(srcDir);
			Util.mkdirs(target.getParent());
			Util.createLink(target, ref.getSource().getFile().toAbsolutePath());
		}

		// create build gradle
		Optional<String> packageName = Util.getSourcePackage(
				new String(Files.readAllBytes(srcFile), Charset.defaultCharset()));
		String baseName = Util.getBaseName(name);
		String fullClassName;
		fullClassName = packageName.map(s -> s + "." + baseName).orElse(baseName);
		String templateName = "build.qute.gradle";
		Path destination = tmpProjectDir.resolve("build.gradle");
		TemplateEngine engine = TemplateEngine.instance();

		// both collectDependencies and repositories are manipulated by
		// resolveDependencies
		List<MavenRepo> repositories = prj.getRepositories();
		if (repositories.isEmpty()) {
			prj.addRepository(DependencyUtil.toMavenRepo("mavencentral"));
		}

		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = dependencies
											.stream()
											.map(JitPackUtil::ensureGAV)
											.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(dependencies)
				&& repositories.stream().noneMatch(r -> DependencyUtil.REPO_JITPACK.equals(r.getUrl()))) {
			prj.addRepository(DependencyUtil.toMavenRepo(DependencyUtil.ALIAS_JITPACK));
		}

		renderTemplate(engine, depIds, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				arguments,
				destination);

		// setup eclipse
		templateName = ".qute.classpath";
		destination = tmpProjectDir.resolve(".classpath");
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				arguments,
				destination);

		templateName = ".qute.project";
		destination = tmpProjectDir.resolve(".project");
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				arguments,
				destination);

		templateName = "main.qute.launch";
		destination = tmpProjectDir.resolve(".eclipse/" + baseName + ".launch");
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				arguments,
				destination);

		templateName = "main-port-4004.qute.launch";
		destination = tmpProjectDir.resolve(".eclipse/" + baseName + "-port-4004.launch");
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				arguments,
				destination);

		// setup vscode
		templateName = "launch.qute.json";
		destination = tmpProjectDir.resolve(".vscode/launch.json");
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					arguments,
					destination);
		}

		// setup vscode
		templateName = "README.qute.md";
		destination = tmpProjectDir.resolve("README.md");
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					arguments,
					destination);
		}

		templateName = "settings.qute.json";
		destination = tmpProjectDir.resolve(".vscode/settings.json");
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					arguments,
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
