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
import dev.jbang.source.*;

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

	static class ResourceFile {
		String originalResource;
		String backingResource;
		String target;

		ResourceFile(RefTarget ref) {
			originalResource = ref.getSource().getOriginalResource();
			backingResource = ref.getSource().getFile().toString();
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

		public ScriptInfo(Code code, RunContext ctx) {
			originalResource = code.getResourceRef().getOriginalResource();

			if (scripts.add(originalResource)) {
				backingResource = code.getResourceRef().getFile().toString();

				Source source = code.asSourceSet().getMainSource();
				init(source);

				if (ctx != null) {
					applicationJar = code.getJarFile() == null ? null : code.getJarFile().getAbsolutePath();
					mainClass = ctx.getMainClassOr(code);
					requestedJavaVersion = code.getJavaVersion().orElse(null);
					availableJdkPath = Objects.toString(JdkManager.getCurrentJdk(requestedJavaVersion), null);

					String cp = ctx.resolveClassPath(code);
					if (cp.isEmpty()) {
						resolvedDependencies = Collections.emptyList();
					} else {
						resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));
					}

					if (ctx.getBuildJdk() > 0) {
						javaVersion = Integer.toString(ctx.getBuildJdk());
					}

					List<String> opts = ctx.getRuntimeOptionsMerged(code);
					if (!opts.isEmpty()) {
						runtimeOptions = opts;
					}
				}
			}
		}

		public ScriptInfo(Source source) {
			init(source);
		}

		private void init(Source source) {
			List<String> deps = source.collectDependencies();
			if (!deps.isEmpty()) {
				dependencies = deps;
			}
			if (!source.collectRepositories().isEmpty()) {
				repositories = source	.collectRepositories()
										.stream()
										.map(Repo::new)
										.collect(Collectors.toList());
			}
			List<RefTarget> refs = source.collectFiles();
			if (!refs.isEmpty()) {
				files = refs.stream()
							.map(ResourceFile::new)
							.collect(Collectors.toList());
			}
			List<Source> srcs = source.collectSources();
			if (!srcs.isEmpty()) {
				sources = srcs	.stream()
								.map(ScriptInfo::new)
								.collect(Collectors.toList());
			}
			if (!source.getCompileOptions().isEmpty()) {
				compileOptions = source.getCompileOptions();
			}
			gav = source.getGav().orElse(null);
			description = source.getDescription().orElse(null);
		}

	}

	private static Set<String> scripts;

	ScriptInfo getInfo() {
		scriptMixin.validate();

		RunContext ctx = getRunContext();
		Code code = ctx.importJarMetadataFor(ctx.forResource(scriptMixin.scriptOrFile));

		scripts = new HashSet<>();

		return new ScriptInfo(code, ctx);
	}

	RunContext getRunContext() {
		RunContext ctx = new RunContext();
		ctx.setProperties(dependencyInfoMixin.getProperties());
		ctx.setAdditionalDependencies(dependencyInfoMixin.getDependencies());
		ctx.setAdditionalRepositories(dependencyInfoMixin.getRepositories());
		ctx.setAdditionalClasspaths(dependencyInfoMixin.getClasspaths());
		ctx.setAdditionalSources(scriptMixin.sources);
		ctx.setAdditionalResources(scriptMixin.resources);
		ctx.setForceJsh(scriptMixin.forcejsh);
		return ctx;
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
