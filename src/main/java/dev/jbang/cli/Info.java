package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;
import static java.lang.System.out;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.DocRef;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.RefTarget;
import dev.jbang.source.SourceSet;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class, Jar.class, Docs.class })
public class Info {
	@CommandLine.Mixin
	HelpMixin helpMixin;
}

abstract class BaseInfoCommand extends BaseCommand {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Option(names = {
			"--build-dir" }, description = "Use given directory for build results")
	Path buildDir;

	@CommandLine.Option(names = {
			"--module" }, arity = "0..1", description = "Treat resource as a module. Optionally with the given module name", preprocessor = StrictParameterPreprocessor.class)
	String module;

	static class ProjectFile {
		String originalResource;
		String backingResource;
		String target;
		String error;

		ProjectFile(ResourceRef ref) {
			originalResource = ref.getOriginalResource();
			backingResource = ref.exists() ? ref.getFile().toString() : null;
			error = ref instanceof ResourceRef.UnresolvableResourceRef
					? ((ResourceRef.UnresolvableResourceRef) ref).getReason()
					: null;
		}

		ProjectFile(RefTarget ref) {
			this(ref.getSource());
			target = Objects.toString(ref.getTarget(), null);
		}
	}

	static class Repo {
		String id;
		String url;

		Repo(MavenRepo repo) {
			id = repo.getId();
			url = repo.getUrl();
		}
	}

	static class ScriptInfo {
		String originalResource;
		String backingResource;
		String applicationJar;
		String applicationJsa;
		String nativeImage;
		String mainClass;
		List<String> dependencies;
		List<Repo> repositories;
		List<String> resolvedDependencies;
		String javaVersion;
		String requestedJavaVersion;
		String availableJdkPath;
		List<String> compileOptions;
		List<String> runtimeOptions;
		List<ProjectFile> files;
		List<ProjectFile> sources;
		String description;
		String gav;
		String module;
		Map<String, List<ProjectFile>> docs;

		public ScriptInfo(Project prj, Path buildDir, boolean assureJdkInstalled) {
			originalResource = prj.getResourceRef().getOriginalResource();

			if (scripts.add(originalResource)) {
				backingResource = prj.getResourceRef().getFile().toString();

				init(prj);

				try {
					BuildContext ctx = BuildContext.forProject(prj, buildDir);
					init(ctx);
				} catch (Exception e) {
					Util.warnMsg("Unable to obtain full information, the script probably contains errors", e);
				}

				try {
					JdkManager jdkMan = JavaUtil.defaultJdkManager();
					Jdk jdk = assureJdkInstalled ? jdkMan.getOrInstallJdk(requestedJavaVersion)
							: jdkMan.getJdk(requestedJavaVersion);
					if (jdk != null && jdk.isInstalled()) {
						availableJdkPath = ((Jdk.InstalledJdk) jdk).home().toString();
					}
				} catch (ExitException e) {
					// Ignore
				}
			}
		}

		private void init(Project prj) {
			if (prj.getMainSource() == null) {
				if (!prj.getRepositories().isEmpty()) {
					repositories = prj.getRepositories()
						.stream()
						.map(Repo::new)
						.collect(Collectors.toList());
				}
			} else {
				init(prj.getMainSourceSet());
			}
			if (!prj.getRepositories().isEmpty()) {
				repositories = prj.getRepositories()
					.stream()
					.map(Repo::new)
					.collect(Collectors.toList());
			}
			gav = prj.getGav().orElse(null);
			description = prj.getDescription().orElse(null);
			docs = getDocsMap(prj.getDocs());

			module = prj.getModuleName().orElse(null);

			mainClass = prj.getMainClass();
			module = ModuleUtil.getModuleName(prj);
			requestedJavaVersion = prj.getJavaVersion();

			if (prj.getJavaVersion() != null) {
				javaVersion = Integer.toString(JavaUtil.parseJavaVersion(prj.getJavaVersion()));
			}

			List<String> opts = prj.getRuntimeOptions();
			if (!opts.isEmpty()) {
				runtimeOptions = opts;
			}
		}

		private void init(SourceSet ss) {
			List<String> deps = ss.getDependencies();
			if (!deps.isEmpty()) {
				dependencies = deps;
			}
			List<RefTarget> refs = ss.getResources();
			if (!refs.isEmpty()) {
				files = refs.stream()
					.map(ProjectFile::new)
					.collect(Collectors.toList());
			}
			List<ResourceRef> srcs = ss.getSources();
			if (!srcs.isEmpty()) {
				sources = srcs.stream()
					.map(ProjectFile::new)
					.collect(Collectors.toList());
			}
			if (!ss.getCompileOptions().isEmpty()) {
				compileOptions = ss.getCompileOptions();
			}
		}

		private void init(BuildContext ctx) {
			applicationJar = ctx.getJarFile() == null ? null
					: ctx.getJarFile().toAbsolutePath().toString();
			applicationJsa = ctx.getJsaFile() != null && Files.isRegularFile(ctx.getJsaFile())
					? ctx.getJsaFile().toAbsolutePath().toString()
					: null;
			nativeImage = ctx.getNativeImageFile() != null && Files.exists(ctx.getNativeImageFile())
					? ctx.getNativeImageFile().toAbsolutePath().toString()
					: null;

			List<ArtifactInfo> artifacts = ctx.resolveClassPath().getArtifacts();
			if (artifacts.isEmpty()) {
				resolvedDependencies = Collections.emptyList();
			} else {
				resolvedDependencies = artifacts
					.stream()
					.map(a -> a.getFile().toString())
					.collect(Collectors.toList());
			}

			if (ctx.getJarFile() != null && Files.exists(ctx.getJarFile())) {
				Project jarProject = Project.builder().build(ctx.getJarFile());
				mainClass = jarProject.getMainClass();
				gav = jarProject.getGav().orElse(gav);
				module = ModuleUtil.getModuleName(jarProject);
			}
		}

		/**
		 * Returns a map of documentation ids to lists of documentation references. Refs
		 * that has no id are grouped under "main".
		 *
		 * @param docs the list of documentation references
		 * @return a map where the key is the documentation id and the value is a list
		 *         of ProjectFile pointing to the documentation files or links
		 */
		Map<String, List<ProjectFile>> getDocsMap(List<DocRef> docs) {
			Map<String, List<ProjectFile>> docsMap = new LinkedHashMap<>();
			if (docs != null) {
				for (DocRef doc : docs) {
					String key = doc.getId() == null ? "main" : doc.getId();
					List<ProjectFile> pfs = docsMap.computeIfAbsent(key, k -> new ArrayList<>());
					pfs.add(new ProjectFile(doc.getRef()));
				}
			}
			return docsMap;
		}

	}

	private static Set<String> scripts;

	ScriptInfo getInfo(boolean assureJdkInstalled) {
		scriptMixin.validate();

		ProjectBuilder pb = createProjectBuilder();
		Project prj = pb.build(scriptMixin.scriptOrFile);

		scripts = new HashSet<>();

		return new ScriptInfo(prj, buildDir, assureJdkInstalled);
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
			.forceType(scriptMixin.forceType)
			.moduleName(module)
			.catalog(scriptMixin.catalog);
	}

}

@CommandLine.Command(name = "tools", description = "Prints a json description usable for tools/IDE's to get classpath and more info for a jbang script/application. Exact format is still quite experimental.")
class Tools extends BaseInfoCommand {

	@CommandLine.Option(names = {
			"--select" }, description = "Indicate the name of the field to select and return from the full info result")
	String select;

	@Override
	public Integer doCall() throws IOException {

		Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		ScriptInfo info = getInfo(true);
		if (select != null) {
			try {
				Field f = info.getClass().getDeclaredField(select);
				Object v = f.get(info);
				if (v != null) {
					if (v instanceof String || v instanceof Number) {
						out.println(v);
					} else {
						parser.toJson(v, out);
					}
				} else {
					// We'll return an error code for `null` so
					// any calling scripts can easily detect that
					// situation instead of having to ambiguously
					// compare against the string "null"
					return EXIT_GENERIC_ERROR;
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new ExitException(EXIT_INVALID_INPUT, "Cannot return value of unknown field: " + select, e);
			}
		} else {
			parser.toJson(info, out);
		}

		return EXIT_OK;
	}
}

@CommandLine.Command(name = "classpath", description = "Prints class-path used for this application using operating system specific path separation.")
class ClassPath extends BaseInfoCommand {

	@CommandLine.Option(names = {
			"--deps-only" }, description = "Only include the dependencies in the output, not the application jar itself")
	boolean dependenciesOnly;

	@Override
	public Integer doCall() throws IOException {

		ScriptInfo info = getInfo(false);
		List<String> cp = new ArrayList<>(info.resolvedDependencies.size() + 1);
		if (!dependenciesOnly && info.applicationJar != null
				&& !info.resolvedDependencies.contains(info.applicationJar)) {
			cp.add(info.applicationJar);
		}
		cp.addAll(info.resolvedDependencies);
		out.println(String.join(CP_SEPARATOR, cp));

		return EXIT_OK;
	}
}

@CommandLine.Command(name = "jar", description = "Prints the path to this application's JAR file.")
class Jar extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {
		ScriptInfo info = getInfo(false);
		out.println(info.applicationJar);
		return EXIT_OK;
	}
}

@CommandLine.Command(name = "docs", description = "Open the documentation file in the default browser.")
class Docs extends BaseInfoCommand {

	@Option(names = {
			"--open" }, negatable = true, defaultValue = "false", description = "Open the (first) documentation file/link in the default browser")
	public boolean open;

	@Override
	public Integer doCall() throws IOException {

		ScriptInfo info = getInfo(false);

		ProjectFile[] toOpen = new ProjectFile[1];

		if (info.description != null) {
			out.println(info.description);
		}

		info.docs.forEach((String id, List<ProjectFile> docs) -> {
			out.println(ConsoleOutput.yellow(id + ":"));
			docs.forEach(doc -> {

				String uripart = doc.backingResource == null || Util.isURL(doc.originalResource) ? doc.originalResource
						: Paths.get(doc.backingResource).toUri().toString();
				String suffix = doc.backingResource != null ? "" : " (not found)";

				if (toOpen[0] == null && "main".equals(id) && doc.backingResource != null) {
					toOpen[0] = doc;
				}

				out.printf("  %s%s%n", uripart, suffix);

			});
		});

		if (toOpen[0] == null) {
			Util.infoMsg("No documentation files found");
			return EXIT_OK;
		}
		if (!open) {
			Util.infoMsg("Use --open to open the documentation file in the default browser.");
			return EXIT_OK;
		}
		if (GraphicsEnvironment.isHeadless()) {
			Util.infoMsg("Cannot open documentation file in browser in headless mode");
			return EXIT_OK;
		}
		try {
			Desktop.getDesktop().browse(getDocsUri(toOpen[0]));
		} catch (IOException e) {
			Util.infoMsg("Documentation file to open not found: " + toOpen[0]);
		}

		return EXIT_OK;
	}

	URI getDocsUri(ProjectFile doc) {
		if (Util.isURL(doc.originalResource)) {
			return URI.create(doc.originalResource);
		}
		return Paths.get(doc.backingResource).toUri();
	}

}
