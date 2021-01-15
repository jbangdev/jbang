package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.Util.isWindows;
import static dev.jbang.Util.verboseMsg;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.jbang.*;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Setup a temporary project to edit script in an IDE.")
public class Edit extends BaseScriptCommand {

	@CommandLine.Option(names = {
			"--live" }, description = "Setup temporary project, regenerate project on dependency changes.")
	boolean live;

	@CommandLine.Option(names = {
			"--open" }, description = "Opens editor/IDE on the temporary project.", fallbackValue = "${JBANG_EDITOR:-}", preprocessor = StrictParameterPreprocessor.class)
	Optional<String> editor;

	@Override
	public Integer doCall() throws IOException {

		if (insecure) {
			enableInsecure();
		}

		script = RunUnit.forResource(scriptOrFile);
		File project = createProjectForEdit(script, false);
		// err.println(project.getAbsolutePath());

		if (editor.isPresent()) {
			if (editor.get().isEmpty()) {
				askAndInstallEditor();
			}
			if ("gitpod".equals(editor.get()) && System.getenv("GITPOD_WORKSPACE_URL") != null) {
				info("Open this url to edit the project in your gitpod session:\n\n"
						+ System.getenv("GITPOD_WORKSPACE_URL") + "#" + project.getAbsolutePath() + "\n\n");
			} else {
				List<String> optionList = new ArrayList<>();
				optionList.addAll(Arrays.asList(editor.get().split(" ")));
				optionList.add(project.getAbsolutePath());

				String[] cmd;
				if (isWindows()) {
					cmd = new String[] { "cmd", "/c", optionList.stream().collect(Collectors.joining(" ")) };
				} else {
					cmd = new String[] { "sh", "-c", optionList.stream().collect(Collectors.joining(" ")) };
				}
				info("Running `" + String.join(" ", cmd) + "`");
				new ProcessBuilder(cmd).start();
			}
		}

		if (!live) {
			out.println(project.getAbsolutePath()); // quit(project.getAbsolutePath());
		} else {
			try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
				File orginalFile = script.script().getScriptResource().getFile();
				if (!orginalFile.exists()) {
					throw new ExitException(2, "Cannot live edit " + script.getScriptResource().getOriginalResource());
				}
				Path watched = orginalFile.getAbsoluteFile().getParentFile().toPath();
				watched.register(watchService,
						new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY });
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
								script = RunUnit.forResource(scriptOrFile);
								createProjectForEdit(script, true);
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

	private void askAndInstallEditor() throws IOException {

		File editorBinPath = EditorManager.getVSCodiumBinPath().toFile();
		File dataPath = EditorManager.getVSCodiumDataPath().toFile();
		Path editorPath = EditorManager.getVSCodiumPath();
		editor = Optional.of(editorBinPath.getAbsolutePath());

		if (!editorBinPath.exists()) {
			String question = "You requested to open default editor but no default editor configured." +
					"\n" +
					"jbang can download and configure a visual studio code with Java support to use\n" +
					"See https://vscodium.com for details\n" +
					"\n" +
					"Do you want jbang to download VSCodium for you into " + editorPath + " ? \n\n" +
					"0) Yes, please." +
					"\n\n" +
					"Any other response will result in exit.\n";

			ConsoleInput con = new ConsoleInput(
					1,
					10,
					TimeUnit.SECONDS);
			Util.infoMsg(question);
			Util.infoMsg("Type in your choice (0) and hit enter. Times out after 10 seconds.");
			String input = con.readLine();

			boolean abort = true;
			try {
				int result = Integer.parseInt(input);
				if (result == 0) {
					abort = false;
				}
			} catch (NumberFormatException ef) {
				Util.errorMsg("Could not parse answer as a number. Aborting");
			}

			if (abort)
				throw new ExitException(10,
						"No default editor configured and automatic download not accepted.\n Please try again accepting the download or use an explicit editor, i.e. `jbang edit --open=eclipse xyz.java`");

			editorPath = EditorManager.downloadAndInstallEditor();

			if (!dataPath.exists()) {
				verboseMsg("Making portable data path " + dataPath.toString());
				dataPath.mkdirs();
			}

			verboseMsg("Installing Java extensions...");
			ProcessBuilder pb = new ProcessBuilder(new String[] {
					editor.get(),
					"--install-extension", "redhat.java",
					"--install-extension", "vscjava.vscode-java-debug",
					"--install-extension", "vscjava.vscode-java-test",
					"--install-extension", "vscjava.vscode-java-dependency"
			});
			pb.inheritIO();
			Process process = pb.start();
			try {
				int exit = process.waitFor();
				if (exit > 0) {
					throw new ExitException(EXIT_INTERNAL_ERROR,
							"Could not install and setup extensions into vscodium. Aborting.");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

	/** Create Project to use for editing **/
	File createProjectForEdit(ExtendedRunUnit script, boolean reload) throws IOException {

		File originalFile = script.getScriptResource().getFile();

		List<String> dependencies = script.collectAllDependencies();
		String cp = script.resolveClassPath(offline);
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		File baseDir = Settings.getCacheDir(Settings.CacheClass.projects).toFile();

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

		if (script.runUnit instanceof Script) {
			for (Script source : script.script().collectAllSources()) {
				File sfile = null;
				if (source.getJavaPackage().isPresent()) {
					File packageDir = new File(srcDir, source.getJavaPackage().get().replace(".", File.separator));
					packageDir.mkdir();
					sfile = new File(packageDir, source.getScriptResource().getFile().getName());
				} else {
					sfile = new File(srcDir, source.getScriptResource().getFile().getName());
				}
				Path destFile = source.getScriptResource().getFile().toPath().toAbsolutePath();
				Util.createLink(sfile.toPath(), destFile);
			}
		}

		// create build gradle
		Optional<String> packageName = Util.getSourcePackage(
				new String(Files.readAllBytes(srcFile), Charset.defaultCharset()));
		String baseName = Util.getBaseName(name);
		String fullClassName;
		if (packageName.isPresent()) {
			fullClassName = packageName.get() + "." + baseName;
		} else {
			fullClassName = baseName;
		}
		String templateName = "build.qute.gradle";
		Path destination = new File(tmpProjectDir, "build.gradle").toPath();
		TemplateEngine engine = Settings.getTemplateEngine();

		// both collectDependencies and repositories are manipulated by
		// resolveDependencies
		List<MavenRepo> repositories = script.script().collectAllRepositories();
		if (repositories.isEmpty()) {
			repositories.add(DependencyUtil.toMavenRepo("jcenter"));
		}

		// Turn any URL dependencies into regular GAV coordinates
		dependencies = dependencies
									.stream()
									.map(JitPackUtil::ensureGAV)
									.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!repositories.stream().anyMatch(r -> DependencyUtil.REPO_JITPACK.equals(r.getUrl()))) {
			repositories.add(DependencyUtil.toMavenRepo(DependencyUtil.ALIAS_JITPACK));
		}

		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				script.getArguments(),
				destination);

		// setup eclipse
		templateName = ".qute.classpath";
		destination = new File(tmpProjectDir, ".classpath").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				script.getArguments(),
				destination);

		templateName = ".qute.project";
		destination = new File(tmpProjectDir, ".project").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				script.getArguments(),
				destination);

		templateName = "main.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + ".launch").toPath();
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				script.getArguments(),
				destination);

		templateName = "main-port-4004.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + "-port-4004.launch").toPath();
		renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
				templateName,
				script.getArguments(),
				destination);

		// setup vscode
		templateName = "launch.qute.json";
		destination = new File(tmpProjectDir, ".vscode/launch.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					script.getArguments(),
					destination);
		}

		// setup vscode
		templateName = "README.qute.md";
		destination = new File(tmpProjectDir, "README.md").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					script.getArguments(),
					destination);
		}

		templateName = "settings.qute.json";
		destination = new File(tmpProjectDir, ".vscode/settings.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, dependencies, fullClassName, baseName, resolvedDependencies, repositories,
					templateName,
					script.getArguments(),
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
			throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
		String result = template
								.data("repositories",
										repositories.stream()
													.map(MavenRepo::getUrl)
													.filter(s -> !"".equals(s)))
								.data("dependencies", collectDependencies)
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

	static String stripPrefix(String fileName) {
		if (fileName.indexOf(".") > 0) {
			return fileName.substring(0, fileName.lastIndexOf("."));
		} else {
			return fileName;
		}
	}
}
