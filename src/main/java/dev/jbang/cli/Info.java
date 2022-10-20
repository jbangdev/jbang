package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.dependencies.MavenRepo;
import dev.jbang.net.JdkManager;
import dev.jbang.source.*;
import dev.jbang.util.JavaUtil;

import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class })
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

		public ScriptInfo(Project prj, ProjectBuilder pb) {
			originalResource = prj.getResourceRef().getOriginalResource();

			if (scripts.add(originalResource)) {
				backingResource = prj.getResourceRef().getFile().toString();

				init(prj);

				if (pb != null) {
					applicationJar = prj.getJarFile() == null ? null : prj.getJarFile().toAbsolutePath().toString();
					nativeImage = prj.getNativeImageFile() == null || !Files.exists(prj.getNativeImageFile()) ? null
							: prj.getNativeImageFile().toAbsolutePath().toString();
					mainClass = prj.getMainClass();
					requestedJavaVersion = prj.getJavaVersion();
					availableJdkPath = Objects.toString(JdkManager.getCurrentJdk(requestedJavaVersion), null);

					String cp = prj.resolveClassPath().getClassPath();
					if (cp.isEmpty()) {
						resolvedDependencies = Collections.emptyList();
					} else {
						resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));
					}

					// TODO remove if everything okay
					// if (prj.isJar() && prj.getBuildJdk() > 0) {
					// javaVersion = Integer.toString(prj.getBuildJdk());
					// }
					if (prj.getJavaVersion() != null) {
						javaVersion = Integer.toString(JavaUtil.parseJavaVersion(prj.getJavaVersion()));
					}

					List<String> opts = prj.getRuntimeOptions();
					if (!opts.isEmpty()) {
						runtimeOptions = opts;
					}
				}
			}
		}

		private void init(Project prj) {
			List<String> deps = prj.resolveClassPath().getClassPaths();
			if (!deps.isEmpty()) {
				dependencies = deps;
			}
			if (prj.getMainSource() == null) {
				if (!prj.getRepositories().isEmpty()) {
					repositories = prj	.getRepositories()
										.stream()
										.map(Repo::new)
										.collect(Collectors.toList());
				}
			} else {
				init(prj.getMainSourceSet());
			}
			if (!prj.getRepositories().isEmpty()) {
				repositories = prj	.getRepositories()
									.stream()
									.map(Repo::new)
									.collect(Collectors.toList());
			}
			gav = prj.getGav().orElse(null);
			description = prj.getDescription().orElse(null);
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
				sources = srcs	.stream()
								.map(ProjectFile::new)
								.collect(Collectors.toList());
			}
			if (!ss.getCompileOptions().isEmpty()) {
				compileOptions = ss.getCompileOptions();
			}
		}

	}

	private static Set<String> scripts;

	ScriptInfo getInfo() {
		scriptMixin.validate();

		ProjectBuilder pb = createProjectBuilder();
		Project prj = pb.build(scriptMixin.scriptOrFile);

		scripts = new HashSet<>();

		return new ScriptInfo(prj, pb);
	}

	ProjectBuilder createProjectBuilder() {
		return ProjectBuilder	.create()
								.setProperties(dependencyInfoMixin.getProperties())
								.additionalDependencies(dependencyInfoMixin.getDependencies())
								.additionalRepositories(dependencyInfoMixin.getRepositories())
								.additionalClasspaths(dependencyInfoMixin.getClasspaths())
								.additionalSources(scriptMixin.sources)
								.additionalResources(scriptMixin.resources)
								.forceType(scriptMixin.forceType)
								.catalog(scriptMixin.catalog)
								.buildDir(buildDir);
	}

}

@CommandLine.Command(name = "tools", description = "Prints a json description usable for tools/IDE's to get classpath and more info for a jbang script/application. Exact format is still quite experimental.")
class Tools extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {

		Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		parser.toJson(getInfo(), System.out);

		return EXIT_OK;
	}
}

@CommandLine.Command(name = "classpath", description = "Prints classpath used for this application using operating system specific path separation.")
class ClassPath extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {

		ScriptInfo info = getInfo();
		List<String> cp = new ArrayList<>(info.resolvedDependencies.size() + 1);
		if (info.applicationJar != null) {
			cp.add(info.applicationJar);
		}
		cp.addAll(info.resolvedDependencies);
		System.out.println(String.join(CP_SEPARATOR, cp));

		return EXIT_OK;
	}
}
