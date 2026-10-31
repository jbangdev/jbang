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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.ExitException;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.DependencyUtil;
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

@CommandDefinition(name = "info", description = "Provides info about the script for tools (and humans who are tools).", groupCommands = {
		Info.Tools.class, Info.ClassPath.class, Info.Jar.class,
		Info.SourcePath.class, Info.SourceJar.class,
		Info.Docs.class }, generateHelp = true)
public class Info extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	static abstract class BaseInfoCommand extends BaseCommand {

		@Mixin
		ScriptMixin scriptMixin;

		@Mixin
		DependencyInfoMixin dependencyInfoMixin;

		@Option(name = "build-dir", description = "Use given directory for build results")
		String buildDir;

		@Option(name = "module", fallbackValue = "", description = "Treat resource as a module. Optionally with the given module name")
		String module;

		@Override
		public void afterParse() {
			super.afterParse();
			dependencyInfoMixin.applyIgnoreTransitiveRepositories();
		}

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
			String applicationSourceJar;
			String applicationJsa;
			String nativeImage;
			String mainClass;
			List<String> dependencies;
			List<Repo> repositories;
			List<String> resolvedDependencies;
			List<String> resolvedSourceDependencies;
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
				this(prj, buildDir, assureJdkInstalled, false);
			}

			public ScriptInfo(Project prj, Path buildDir, boolean assureJdkInstalled, boolean downloadSources) {
				originalResource = prj.getResourceRef().getOriginalResource();

				if (scripts.add(originalResource)) {
					backingResource = prj.getResourceRef().getFile().toString();

					init(prj);

					try {
						BuildContext ctx = BuildContext.forProject(prj, buildDir);
						init(ctx, downloadSources);
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
				if (prj.getMainSource() != null) {
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

			private void init(BuildContext ctx, boolean downloadSources) {
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
					resolvedSourceDependencies = Collections.emptyList();
				} else {
					resolvedDependencies = artifacts
						.stream()
						.map(a -> a.getFile().toString())
						.collect(Collectors.toList());
					resolvedSourceDependencies = artifacts
						.stream()
						.map(ArtifactInfo::getSourceFile)
						.filter(Objects::nonNull)
						.filter(Files::exists)
						.map(Path::toString)
						.collect(Collectors.toList());
				}

				if (ctx.getJarFile() != null && Files.exists(ctx.getJarFile())) {
					Project jarProject = Project.builder().build(ctx.getJarFile());
					mainClass = jarProject.getMainClass();
					gav = jarProject.getGav().orElse(gav);
					module = ModuleUtil.getModuleName(jarProject);
				}

				if (downloadSources) {
					resolveApplicationSourceJar(ctx);
				}
			}

			private void resolveApplicationSourceJar(BuildContext ctx) {
				Project prj = ctx.getProject();
				if (!prj.isExecutableArchive()) {
					return;
				}
				Path jar = ctx.getJarFile();
				if (jar == null || !Files.exists(jar)) {
					return;
				}

				String fileName = jar.getFileName().toString();
				if (fileName.endsWith(".jar")) {
					Path sibling = jar.resolveSibling(fileName.substring(0, fileName.length() - 4) + "-sources.jar");
					if (Files.isRegularFile(sibling)) {
						applicationSourceJar = sibling.toAbsolutePath().toString();
						return;
					}
				}

				List<ArtifactInfo> artifacts = ctx.resolveClassPath().getArtifacts();
				for (ArtifactInfo art : artifacts) {
					if (jar.equals(art.getFile())) {
						if (art.getSourceFile() != null && Files.exists(art.getSourceFile())) {
							applicationSourceJar = art.getSourceFile().toAbsolutePath().toString();
							return;
						} else if (art.isSourcesChecked()) {
							return;
						}
						break;
					}
				}

				String targetGav = gav != null ? gav : prj.getGav().orElse(null);
				if (targetGav == null && prj.getResourceRef().getOriginalResource() != null
						&& DependencyUtil.looksLikeAGav(prj.getResourceRef().getOriginalResource())) {
					targetGav = prj.getResourceRef().getOriginalResource();
				}
				if (targetGav != null) {
					Optional<Path> srcJar = DependencyUtil.resolveSource(targetGav, prj.getRepositories());
					if (srcJar.isPresent() && Files.exists(srcJar.get())) {
						applicationSourceJar = srcJar.get().toAbsolutePath().toString();
					}
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
			return getInfo(assureJdkInstalled, false);
		}

		ScriptInfo getInfo(boolean assureJdkInstalled, boolean downloadSources) {
			scriptMixin.validate();
			if (downloadSources) {
				Util.setDownloadSources(true);
			}
			ProjectBuilder pb = createProjectBuilder();
			Project prj = pb.build(scriptMixin.scriptOrFile);

			scripts = new HashSet<>();

			Path bd = buildDir != null ? Paths.get(buildDir) : null;
			return new ScriptInfo(prj, bd, assureJdkInstalled, downloadSources || Util.downloadSources());
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
				.forceType(scriptMixin.getForceType())
				.moduleName(module)
				.catalog(scriptMixin.catalog);
		}

	}

	@CommandDefinition(name = "tools", description = "Prints a json description usable for tools/IDE's to get classpath and more info for a jbang script/application.", generateHelp = true)
	public static class Tools extends BaseInfoCommand {

		@Option(name = "select", description = "Indicate the name of the field to select and return from the full info result")
		String select;

		@Option(name = "download-sources", hasValue = false, description = "Resolve and include source JARs in the output")
		boolean downloadSources;

		@Override
		public Integer doCall() throws IOException {

			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			boolean needSources = downloadSources
					|| "applicationSourceJar".equals(select)
					|| "resolvedSourceDependencies".equals(select);
			ScriptInfo info = getInfo(true, needSources);
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
						return ExitException.EXIT_GENERIC_ERROR;
					}
				} catch (NoSuchFieldException | IllegalAccessException e) {
					throw new ExitException(ExitException.EXIT_INVALID_INPUT,
							"Cannot return value of unknown field: " + select, e);
				}
			} else {
				parser.toJson(info, out);
			}

			return ExitException.EXIT_OK;
		}
	}

	@CommandDefinition(name = "classpath", description = "Prints class-path used for this application using operating system specific path separation.", generateHelp = true)
	public static class ClassPath extends BaseInfoCommand {

		@Option(name = "deps-only", hasValue = false, description = "Only include the dependencies in the output, not the application jar itself")
		boolean dependenciesOnly;

		@Override
		public Integer doCall() throws IOException {

			ScriptInfo info = getInfo(false);
			List<String> deps = info.resolvedDependencies != null ? info.resolvedDependencies
					: Collections.emptyList();
			List<String> cp = new ArrayList<>(deps.size() + 1);
			if (!dependenciesOnly && info.applicationJar != null
					&& !deps.contains(info.applicationJar)) {
				cp.add(info.applicationJar);
			}
			cp.addAll(deps);
			out.println(String.join(CP_SEPARATOR, cp));

			return ExitException.EXIT_OK;
		}
	}

	@CommandDefinition(name = "jar", description = "Prints the path to this application's JAR file.", generateHelp = true)
	public static class Jar extends BaseInfoCommand {

		@Override
		public Integer doCall() throws IOException {
			ScriptInfo info = getInfo(false);
			out.println(info.applicationJar);
			return ExitException.EXIT_OK;
		}
	}

	@CommandDefinition(name = "source-path", aliases = { "sourcepath",
			"sources-path" }, description = "Prints source-path used for this application using operating system specific path separation.", generateHelp = true)
	public static class SourcePath extends BaseInfoCommand {

		@Option(name = "deps-only", hasValue = false, description = "Only include the dependencies in the output, not the application jar itself")
		boolean dependenciesOnly;

		@Override
		public Integer doCall() throws IOException {

			ScriptInfo info = getInfo(false, true);
			List<String> deps = info.resolvedSourceDependencies != null ? info.resolvedSourceDependencies
					: Collections.emptyList();
			List<String> sp = new ArrayList<>(deps.size() + 1);
			if (!dependenciesOnly && info.applicationSourceJar != null
					&& !deps.contains(info.applicationSourceJar)) {
				sp.add(info.applicationSourceJar);
			}
			sp.addAll(deps);
			out.println(String.join(CP_SEPARATOR, sp));

			return ExitException.EXIT_OK;
		}
	}

	@CommandDefinition(name = "source-jar", aliases = { "sourcejar",
			"sources-jar" }, description = "Prints the path to this application's or artifact's source JAR file.", generateHelp = true)
	public static class SourceJar extends BaseInfoCommand {

		@Override
		public Integer doCall() throws IOException {
			ScriptInfo info = getInfo(false, true);
			if (info.applicationSourceJar != null) {
				out.println(info.applicationSourceJar);
				return ExitException.EXIT_OK;
			} else {
				throw new ExitException(ExitException.EXIT_GENERIC_ERROR,
						"No source JAR found for: " + scriptMixin.scriptOrFile);
			}
		}
	}

	@CommandDefinition(name = "docs", description = "Open the documentation file in the default browser.", generateHelp = true)
	public static class Docs extends BaseInfoCommand {

		@Option(name = "open", hasValue = false, negatable = true, description = "Open the (first) documentation file/link in the default browser")
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

					String uripart = doc.backingResource == null || Util.isURL(doc.originalResource)
							? doc.originalResource
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
				return ExitException.EXIT_OK;
			}
			if (!open) {
				Util.infoMsg("Use --open to open the documentation file in the default browser.");
				return ExitException.EXIT_OK;
			}
			if (GraphicsEnvironment.isHeadless()) {
				Util.infoMsg("Cannot open documentation file in browser in headless mode");
				return ExitException.EXIT_OK;
			}
			try {
				Desktop.getDesktop().browse(getDocsUri(toOpen[0]));
			} catch (IOException e) {
				Util.infoMsg("Documentation file to open not found: " + toOpen[0]);
			}

			return ExitException.EXIT_OK;
		}

		URI getDocsUri(ProjectFile doc) {
			if (Util.isURL(doc.originalResource)) {
				return URI.create(doc.originalResource);
			}
			return Paths.get(doc.backingResource).toUri();
		}

	}
}
