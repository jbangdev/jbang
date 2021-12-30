package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
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
import dev.jbang.source.RefTarget;
import dev.jbang.source.RunContext;
import dev.jbang.source.ScriptSource;
import dev.jbang.source.Source;

import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class })
public class Info {
}

abstract class BaseInfoCommand extends BaseScriptCommand {

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	class ResourceFile {
		String originalResource;
		String backingResource;
		String target;

		ResourceFile(RefTarget ref) {
			originalResource = ref.getSource().getOriginalResource();
			backingResource = ref.getSource().getFile().toString();
			target = Objects.toString(ref.getTarget(), null);
		}
	}

	class Repo {
		String id;
		String url;

		Repo(MavenRepo repo) {
			id = repo.getId();
			url = repo.getUrl();
		}
	}

	class ScriptInfo {
		String originalResource;
		String backingResource;
		String applicationJar;
		String mainClass;
		List<String> dependencies;
		List<Repo> repositories;
		List<String> resolvedDependencies;
		String javaVersion;
		String requestedJavaVersion;
		String availableJdkPath;
		List<String> compileOptions;
		List<String> runtimeOptions;
		List<ResourceFile> files;
		List<ScriptInfo> sources;
		String description;
		String gav;

		public ScriptInfo(Source src, RunContext ctx) {
			originalResource = src.getResourceRef().getOriginalResource();

			if (scripts.add(originalResource)) {
				backingResource = src.getResourceRef().getFile().toString();

				ScriptSource ss = src.asScriptSource();
				List<String> deps = ss.collectDependencies();
				if (!deps.isEmpty()) {
					dependencies = deps;
				}
				if (!ss.collectRepositories().isEmpty()) {
					repositories = ss	.collectRepositories()
										.stream()
										.map(repo -> new Repo(repo))
										.collect(Collectors.toList());
				}
				List<RefTarget> refs = ss.collectFiles();
				if (!refs.isEmpty()) {
					files = refs.stream()
								.map(ref -> new ResourceFile(ref))
								.collect(Collectors.toList());
				}
				List<ScriptSource> srcs = ss.collectSources();
				if (!srcs.isEmpty()) {
					sources = srcs	.stream()
									.map(s -> new ScriptInfo(s, null))
									.collect(Collectors.toList());
				}
				if (!ss.getCompileOptions().isEmpty()) {
					compileOptions = ss.getCompileOptions();
				}
				gav = ss.getGav().orElse(null);
				description = ss.getDescription().orElse(null);

				if (ctx != null) {
					applicationJar = src.getJarFile() == null ? null : src.getJarFile().getAbsolutePath();
					mainClass = ctx.getMainClassOr(src);
					requestedJavaVersion = src.getJavaVersion();
					availableJdkPath = Objects.toString(JdkManager.getCurrentJdk(requestedJavaVersion), null);

					String cp = ctx.resolveClassPath(src);
					if (cp.isEmpty()) {
						resolvedDependencies = Collections.emptyList();
					} else {
						resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));
					}

					if (ctx.getBuildJdk() > 0) {
						javaVersion = Integer.toString(ctx.getBuildJdk());
					}

					List<String> opts = ctx.getRuntimeOptionsMerged(src);
					if (!opts.isEmpty()) {
						runtimeOptions = opts;
					}
				}
			}
		}
	}

	private static Set<String> scripts;

	ScriptInfo getInfo() {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(null, null,
				dependencyInfoMixin.getProperties(),
				dependencyInfoMixin.getDependencies(),
				dependencyInfoMixin.getRepositories(),
				dependencyInfoMixin.getClasspaths(),
				forcejsh);
		Source src = ctx.importJarMetadataFor(ctx.forResource(scriptOrFile));

		scripts = new HashSet<>();
		ScriptInfo info = new ScriptInfo(src, ctx);

		return info;
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
