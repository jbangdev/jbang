package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.source.*;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;

import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class, Jar.class })
public class Info {
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
			"--module" }, arity = "0..1", fallbackValue = "", description = "Treat resource as a module. Optionally with the given module name", preprocessor = StrictParameterPreprocessor.class)
	String module;

	static class ProjectFile {
		String originalResource;
		String backingResource;
		String target;

		ProjectFile(ResourceRef ref) {
			originalResource = ref.getOriginalResource();
			backingResource = ref.getFile().toString();
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

		public ScriptInfo(BuildContext ctx, boolean assureJdkInstalled) {
			Project prj = ctx.getProject();
			originalResource = prj.getResourceRef().getOriginalResource();

			if (scripts.add(originalResource)) {
				backingResource = prj.getResourceRef().getFile().toString();

				init(ctx);

				applicationJar = ctx.getJarFile() == null ? null
						: ctx.getJarFile().toAbsolutePath().toString();
				applicationJsa = ctx.getJsaFile() != null && Files.isRegularFile(ctx.getJsaFile())
						? ctx.getJsaFile().toAbsolutePath().toString()
						: null;
				nativeImage = ctx.getNativeImageFile() != null && Files.exists(ctx.getNativeImageFile())
						? ctx.getNativeImageFile().toAbsolutePath().toString()
						: null;
				mainClass = prj.getMainClass();
				module = ModuleUtil.getModuleName(prj);
				requestedJavaVersion = prj.getJavaVersion();

				try {
					JdkManager jdkMan = JavaUtil.defaultJdkManager();
					Jdk jdk = assureJdkInstalled ? jdkMan.getOrInstallJdk(requestedJavaVersion)
							: jdkMan.getJdk(requestedJavaVersion);
					if (jdk != null && jdk.isInstalled()) {
						availableJdkPath = jdk.home().toString();
					}
				} catch (ExitException e) {
					// Ignore
				}

				List<ArtifactInfo> artifacts = ctx.resolveClassPath().getArtifacts();
				if (artifacts.isEmpty()) {
					resolvedDependencies = Collections.emptyList();
				} else {
					resolvedDependencies = artifacts
						.stream()
						.map(a -> a.getFile().toString())
						.collect(Collectors.toList());
				}

				if (prj.getJavaVersion() != null) {
					javaVersion = Integer.toString(JavaUtil.parseJavaVersion(prj.getJavaVersion()));
				}

				List<String> opts = prj.getRuntimeOptions();
				if (!opts.isEmpty()) {
					runtimeOptions = opts;
				}

				if (ctx.getJarFile() != null && Files.exists(ctx.getJarFile())) {
					Project jarProject = Project.builder().build(ctx.getJarFile());
					mainClass = jarProject.getMainClass();
					gav = jarProject.getGav().orElse(gav);
					module = ModuleUtil.getModuleName(jarProject);
				}
			}
		}

		private void init(BuildContext ctx) {
			Project prj = ctx.getProject();
			List<String> deps = ctx.resolveClassPath().getClassPaths();
			if (!deps.isEmpty()) {
				dependencies = deps;
			}
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
			module = prj.getModuleName().orElse(null);
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

	}

	private static Set<String> scripts;

	ScriptInfo getInfo(boolean assureJdkInstalled) {
		scriptMixin.validate();
		dependencyInfoMixin.validate();

		ProjectBuilder pb = createProjectBuilder();
		Project prj = pb.build(scriptMixin.scriptOrFile);

		scripts = new HashSet<>();

		return new ScriptInfo(BuildContext.forProject(prj, buildDir), assureJdkInstalled);
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
						System.out.println(v);
					} else {
						parser.toJson(v, System.out);
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
			parser.toJson(info, System.out);
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
		System.out.println(String.join(CP_SEPARATOR, cp));

		return EXIT_OK;
	}
}

@CommandLine.Command(name = "jar", description = "Prints the path to this application's JAR file.")
class Jar extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {
		ScriptInfo info = getInfo(false);
		System.out.println(info.applicationJar);
		return EXIT_OK;
	}
}
