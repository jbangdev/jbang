package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.Util.isWindows;
import static java.lang.System.out;

import java.io.File;
import java.io.IOException;
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
import java.util.stream.Collectors;

import com.sun.nio.file.SensitivityWatchEventModifier;

import dev.jbang.ExitException;
import dev.jbang.FileRef;
import dev.jbang.Script;
import dev.jbang.Settings;
import dev.jbang.TemplateEngine;
import dev.jbang.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Setup a temporary project to edit script in an IDE.")
public class Edit extends BaseScriptCommand {

	@CommandLine.Option(names = {
			"--live" }, description = "Setup temporary project, regenerate project on dependency changes.")
	boolean live;

	@CommandLine.Option(names = {
			"--open" }, description = "Opens editor/IDE on the temporary project.")
	String editor;

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams);
		File project = createProjectForEdit(script, false);
		// err.println(project.getAbsolutePath());

		if (editor != null) {
			if (editor.isEmpty()) {
				editor = System	.getenv()
								.getOrDefault("JBANG_EDITOR",
										System	.getenv()
												.getOrDefault("VISUAL",
														System.getenv().getOrDefault("EDITOR", "")));
			}
			if ("gitpod".equals(editor) && System.getenv("GITPOD_WORKSPACE_URL") != null) {
				info("Open this url to edit the project in your gitpod session:\n\n"
						+ System.getenv("GITPOD_WORKSPACE_URL") + "#" + project.getAbsolutePath() + "\n\n");
			} else {
				List<String> optionList = new ArrayList<>();
				optionList.addAll(Arrays.asList(editor.split(" ")));
				optionList.add(project.getAbsolutePath());

				String[] cmd;
				if (isWindows()) {
					cmd = new String[] { "cmd", "/c", optionList.stream().collect(Collectors.joining(" ")) };
				} else {
					cmd = new String[] { "sh", "-c", optionList.stream().collect(Collectors.joining(" ")) };
				}
				info("Running `" + String.join(" ", cmd) + "`");
				Process process = new ProcessBuilder(cmd).start();
			}
		}

		if (!live) {
			out.println("echo " + project.getAbsolutePath()); // quit(project.getAbsolutePath());
		} else {
			try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
				File orginalFile = new File(script.getOriginalFile());
				if (!orginalFile.exists()) {
					throw new ExitException(2, "Cannot live edit " + script.getOriginalFile());
				}
				Path watched = orginalFile.getAbsoluteFile().getParentFile().toPath();
				final WatchKey watchKey = watched.register(watchService,
						new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY },
						SensitivityWatchEventModifier.HIGH);
				info("Watching for changes in " + watched);
				while (true) {
					final WatchKey wk = watchService.take();
					for (WatchEvent<?> event : wk.pollEvents()) {
						// we only register "ENTRY_MODIFY" so the context is always a Path.
						final Path changed = (Path) event.context();
						// info(changed.toString());
						if (Files.isSameFile(orginalFile.toPath(), changed)) {
							try {
								// TODO only regenerate when dependencies changes.
								info("Regenerating project.");
								script = prepareScript(scriptOrFile, userParams);
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
		return EXIT_EXECUTE;
	}

	/** Create Project to use for editing **/
	File createProjectForEdit(Script script, boolean reload) throws IOException {

		File originalFile = new File(script.getOriginalFile());

		List<String> collectDependencies = script.collectDependencies();
		String cp = script.resolveClassPath(offline);
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		File baseDir = Settings.getCacheDir(Settings.CacheClass.projects).toFile();

		String name = originalFile.getName();
		name = unkebabify(name);

		File tmpProjectDir = new File(baseDir, name + "_jbang_" +
				Util.getStableID(originalFile.getAbsolutePath()));
		tmpProjectDir.mkdirs();
		tmpProjectDir = new File(tmpProjectDir, stripPrefix(name));
		tmpProjectDir.mkdirs();

		File srcDir = new File(tmpProjectDir, "src");
		srcDir.mkdir();

		Path srcFile = srcDir.toPath().resolve(name);
		Util.createLink(srcFile, originalFile.toPath());

		for (FileRef source : script.collectSources()) {
			File sfile = new File(srcDir, source.getDestination());
			Path destFile = new File(originalFile.getAbsoluteFile().getParent(),
					source.getDestination()).getAbsoluteFile()
											.toPath();
			Util.createLink(sfile.toPath(), destFile);
		}

		// create build gradle
		String baseName = Util.getBaseName(name);
		String templateName = "build.qute.gradle";
		Path destination = new File(tmpProjectDir, "build.gradle").toPath();
		TemplateEngine engine = Settings.getTemplateEngine();

		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, script.getArguments(),
				destination);

		// setup eclipse
		templateName = ".qute.classpath";
		destination = new File(tmpProjectDir, ".classpath").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, script.getArguments(),
				destination);

		templateName = ".qute.project";
		destination = new File(tmpProjectDir, ".project").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, script.getArguments(),
				destination);

		templateName = "main.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + ".launch").toPath();
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, script.getArguments(),
				destination);

		templateName = "main-port-4004.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + "-port-4004.launch").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, script.getArguments(),
				destination);

		// setup vscode
		templateName = "launch.qute.json";
		destination = new File(tmpProjectDir, ".vscode/launch.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName,
					script.getArguments(),
					destination);
		}

		templateName = "settings.qute.json";
		destination = new File(tmpProjectDir, ".vscode/settings.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName,
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

	private void renderTemplate(TemplateEngine engine, List<String> collectDependencies, String baseName,
			List<String> resolvedDependencies, String templateName,
			List<String> userParams, Path destination)
			throws IOException {
		Template template = engine.getTemplate(templateName);
		if (template == null)
			throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
		String result = template
								.data("dependencies", collectDependencies)
								.data("baseName", baseName)
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
