package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.dependencies.*;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.source.*;
import dev.jbang.source.sources.KotlinSource;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build or the set of the sources to a project.", subcommands = {
		ExportPortable.class,
		ExportLocal.class, ExportMavenPublish.class, ExportNative.class, ExportFatjar.class, ExportJlink.class,
		ExportGradleProject.class, ExportMavenProject.class })
public class Export {
	@CommandLine.Mixin
	HelpMixin helpMixin;
}

abstract class BaseExportCommand extends BaseCommand {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	protected Manifest createManifest(String newPath) {
		// Create a Manifest with a Class-Path option
		Manifest mf = new Manifest();
		// without MANIFEST_VERSION nothing is saved.
		mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mf.getMainAttributes().putValue(Attributes.Name.CLASS_PATH.toString(), newPath);
		return mf;
	}

	@Override
	public Integer doCall() throws IOException {
		exportMixin.validate();
		ProjectBuilder pb = createProjectBuilder(exportMixin);
		Project prj = pb.build(exportMixin.scriptMixin.scriptOrFile);
		BuildContext ctx = BuildContext.forProject(prj);
		Project.codeBuilder(ctx).build();
		if (prj.getResourceRef() instanceof AliasResourceResolver.AliasedResourceRef) {
			Alias alias = ((AliasResourceResolver.AliasedResourceRef) prj.getResourceRef()).getAlias();
			if (prj.getMainClass() == null) {
				prj.setMainClass(alias.mainClass);
			}
		}
		return apply(ctx);
	}

	abstract int apply(BuildContext ctx) throws IOException;

	protected ProjectBuilder createProjectBuilder(ExportMixin exportMixin) {
		return Project
			.builder()
			.setProperties(exportMixin.dependencyInfoMixin.getProperties())
			.additionalDependencies(exportMixin.dependencyInfoMixin.getDependencies())
			.additionalRepositories(exportMixin.dependencyInfoMixin.getRepositories())
			.additionalClasspaths(exportMixin.dependencyInfoMixin.getClasspaths())
			.additionalSources(exportMixin.scriptMixin.sources)
			.additionalResources(exportMixin.scriptMixin.resources)
			.forceType(exportMixin.scriptMixin.forceType)
			.catalog(exportMixin.scriptMixin.catalog)
			.javaVersion(exportMixin.buildMixin.javaVersion)
			.mainClass(exportMixin.buildMixin.main)
			.moduleName(exportMixin.buildMixin.module)
			.compileOptions(exportMixin.buildMixin.compileOptions)
			.jdkManager(exportMixin.buildMixin.jdkProvidersMixin.getJdkManager());
	}

	Path getJarOutputPath() {
		Path outputPath = exportMixin.getOutputPath("");
		// Ensure the file ends in `.jar`
		if (!outputPath.toString().endsWith(".jar")) {
			outputPath = Paths.get(outputPath + ".jar");
		}
		return outputPath;
	}
}

@Command(name = "local", description = "Exports jar with classpath referring to local machine dependent locations")
class ExportLocal extends BaseExportCommand {

	@Override
	int apply(BuildContext ctx) throws IOException {
		// Copy the JAR
		Path source = ctx.getJarFile();
		Path outputPath = getJarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.errorMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		} else {
			Util.mkdirs(outputPath.getParent());
		}
		Files.copy(source, outputPath);

		// Update the JAR's MANIFEST.MF Class-Path to point to
		// its dependencies
		Project prj = ctx.getProject();
		String newPath = ctx.resolveClassPath().getManifestPath();
		if (!newPath.isEmpty()) {
			Util.infoMsg("Updating jar...");
			JarUtil.updateJar(outputPath, createManifest(newPath), prj.getMainClass(),
					exportMixin.buildMixin.getProjectJdk(prj));
		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}
}

@Command(name = "portable", description = "Exports jar together with dependencies in way that makes it portable")
class ExportPortable extends BaseExportCommand {

	public static final String LIB = "lib";

	@Override
	int apply(BuildContext ctx) throws IOException {
		// Copy the JAR
		Path source = ctx.getJarFile();
		Path outputPath = getJarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.errorMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		} else {
			Util.mkdirs(outputPath.getParent());
		}

		Files.copy(source, outputPath);
		Project prj = ctx.getProject();
		List<ArtifactInfo> deps = ctx.resolveClassPath().getArtifacts();
		if (!deps.isEmpty()) {
			// Copy dependencies to "./lib" dir
			Path libDir = outputPath.getParent().resolve(LIB);
			Util.mkdirs(libDir);
			StringBuilder newPath = new StringBuilder();
			for (ArtifactInfo dep : deps) {
				if (exportMixin.force) {
					Files.copy(dep.getFile(), libDir.resolve(dep.getFile().getFileName()),
							StandardCopyOption.REPLACE_EXISTING);
				} else {
					Files.copy(dep.getFile(), libDir.resolve(dep.getFile().getFileName()));
				}
				newPath.append(" " + LIB + "/" + dep.getFile().getFileName());
			}

			Util.infoMsg("Updating jar...");
			JarUtil.updateJar(outputPath, createManifest(newPath.toString()), prj.getMainClass(),
					exportMixin.buildMixin.getProjectJdk(prj));
		}
		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}
}

@Command(name = "mavenrepo", description = "Exports directory that can be used to publish as a maven repository")
class ExportMavenPublish extends BaseExportCommand {

	@CommandLine.Option(names = { "--group", "-g" }, description = "The group ID to use for the generated POM.")
	String group;

	@CommandLine.Option(names = { "--artifact", "-a" }, description = "The artifact ID to use for the generated POM.")
	String artifact;

	@CommandLine.Option(names = { "--version", "-v" }, description = "The version to use for the generated POM.")
	String version;

	@Override
	int apply(BuildContext ctx) throws IOException {
		Path outputPath = exportMixin.outputFile;

		if (outputPath == null) {
			outputPath = ArtifactResolver.getLocalMavenRepo();
		}
		// Copy the JAR
		Path source = ctx.getJarFile();

		if (!outputPath.toFile().isDirectory()) {
			if (outputPath.toFile().exists()) {
				Util.errorMsg("Cannot export as maven repository as " + outputPath + " is not a directory.");
				return EXIT_INVALID_INPUT;
			}
			if (exportMixin.force) {
				Util.mkdirs(outputPath);
			} else {
				Util.errorMsg("Cannot export as " + outputPath + " does not exist. Use --force to create.");
				return EXIT_INVALID_INPUT;
			}
		}

		Project prj = ctx.getProject();
		if (prj.getGav().isPresent()) {
			MavenCoordinate coord = MavenCoordinate.fromString(prj.getGav().get()).withVersion();
			if (group == null) {
				group = coord.getGroupId();
			}
			if (artifact == null) {
				artifact = coord.getArtifactId();
			}
			if (version == null) {
				version = coord.getVersion();
			}
		}

		if (group == null) {
			Util.errorMsg(
					"Cannot export as maven repository as no group specified. Add --group=<group id> and run again.");
			return EXIT_INVALID_INPUT;
		}
		Path groupdir = outputPath.resolve(Paths.get(group.replace(".", "/")));

		artifact = artifact != null ? artifact
				: Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
		Path artifactDir = groupdir.resolve(artifact);

		version = version != null ? version : MavenCoordinate.DEFAULT_VERSION;
		Path versionDir = artifactDir.resolve(version);

		String suffix = source.getFileName()
			.toString()
			.substring(source.getFileName().toString().lastIndexOf("."));
		Path artifactFile = versionDir.resolve(artifact + "-" + version + suffix);

		artifactFile.getParent().toFile().mkdirs();

		if (artifactFile.toFile().exists()) {
			if (exportMixin.force) {
				artifactFile.toFile().delete();
			} else {
				Util.errorMsg("Cannot export as " + artifactFile + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}
		Util.infoMsg("Writing " + artifactFile);
		Files.copy(source, artifactFile);

		// generate pom.xml ... if jar could technically just copy from the jar ...but
		// not possible when native thus for now just regenerate it
		Template pomTemplate = TemplateEngine.instance()
			.getTemplate(ResourceRef.forResource("classpath:/pom.qute.xml"));

		Path pomPath = versionDir.resolve(artifact + "-" + version + ".pom");
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {

			String pomfile = pomTemplate
				.data("baseName",
						Util.getBaseName(
								prj.getResourceRef().getFile().getFileName().toString()))
				.data("group", group)
				.data("artifact", artifact)
				.data("version", version)
				.data("description", prj.getDescription().orElse(""))
				.data("dependencies", ctx.resolveClassPath().getArtifacts())
				.render();
			Util.infoMsg("Writing " + pomPath);
			Util.writeString(pomPath, pomfile);

		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}
}

@Command(name = "native", description = "Exports native executable")
class ExportNative extends BaseExportCommand {

	@Override
	int apply(BuildContext ctx) throws IOException {
		// Copy the native binary
		Path source = ctx.getNativeImageFile();
		Path outputPath = getNativeOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.errorMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		} else {
			Util.mkdirs(outputPath.getParent());
		}
		Files.copy(source, outputPath);

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}

	@Override
	protected ProjectBuilder createProjectBuilder(ExportMixin exportMixin) {
		ProjectBuilder pb = super.createProjectBuilder(exportMixin);
		pb.nativeImage(true);
		return pb;
	}

	Path getNativeOutputPath() {
		Path outputPath = exportMixin.getOutputPath("");
		// Ensure that on Windows the file ends in `.exe`
		if (Util.isWindows() && !outputPath.toString().endsWith(".exe")) {
			outputPath = Paths.get(outputPath + ".exe");
		}
		return outputPath;
	}
}

@Command(name = "fatjar", description = "Exports an executable jar with all necessary dependencies included inside")
class ExportFatjar extends BaseExportCommand {

	@Override
	int apply(BuildContext ctx) throws IOException {
		// Copy the native binary
		Path source = ctx.getJarFile();
		Path outputPath = getFatjarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.errorMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		} else {
			Util.mkdirs(outputPath.getParent());
		}

		Project prj = ctx.getProject();
		List<ArtifactInfo> deps = ctx.resolveClassPath().getArtifacts();
		if (!deps.isEmpty()) {
			// Extract main jar and all dependencies to a temp dir
			Path tmpDir = Files.createTempDirectory("fatjar");
			try {
				Util.verboseMsg("Unpacking main jar: " + source);
				UnpackUtil.unzip(source, tmpDir, false, null, ExportFatjar::handleZipFile);
				for (ArtifactInfo dep : deps) {
					Util.verboseMsg("Unpacking artifact: " + dep);
					UnpackUtil.unzip(dep.getFile(), tmpDir, false, null, ExportFatjar::handleZipFile);
				}
				// Remove all signature files
				Path metaInf = tmpDir.resolve("META-INF");
				if (Files.exists(metaInf) && Files.isDirectory(metaInf)) {
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(metaInf, "*.{SF,DSA,RSA}")) {
						for (Path entry : stream) {
							Util.verboseMsg("Removing signature file: " + entry);
							Files.delete(entry);
						}
					}
				}
				Util.verboseMsg("Creating jar: " + outputPath);
				JarUtil.createJar(outputPath, tmpDir, null, prj.getMainClass(),
						exportMixin.buildMixin.getProjectJdk(prj));
			} finally {
				Util.deletePath(tmpDir, true);
			}
		} else {
			// No dependencies so we simply copy the main jar
			Files.copy(source, outputPath);
		}

		Util.infoMsg("Exported to " + outputPath);
		Util.infoMsg("This is an experimental feature and might not to work for certain applications!");
		Util.infoMsg("Help us improve by reporting any issue you find at https://github.com/jbangdev/jbang/issues");
		return EXIT_OK;
	}

	public static void handleZipFile(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile, Exception ex)
			throws IOException {
		if (!zipEntry.isDirectory() && outFile.getFileName().toString().equals("module-info.class")) {
			Util.verboseMsg("Skipping module-info.class");
			return;
		}
		if (!Files.exists(outFile) && !(ex instanceof FileAlreadyExistsException)) {
			UnpackUtil.defaultZipEntryCopy(zipFile, zipEntry, outFile, ex);
			return;
		}
		if (Files.isDirectory(outFile) != zipEntry.isDirectory()) {
			Util.warnMsg("Skipping conflicting duplicate file vs directory: " + zipEntry.getName());
			return;
		}
		if (zipEntry.getName().startsWith("META-INF/services/")) {
			Util.verboseMsg("Merging service files: " + zipEntry.getName());
			try (ReadableByteChannel readableByteChannel = Channels.newChannel(zipFile.getInputStream(zipEntry));
					FileOutputStream fileOutputStream = new FileOutputStream(outFile.toFile(), true)) {
				fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
			}
		} else {
			Util.verboseMsg("Skipping duplicate file: " + zipEntry.getName());
		}
	}

	private Path getFatjarOutputPath() {
		Path outputPath = exportMixin.getOutputPath("-fatjar");
		// Ensure the file ends in `.jar`
		if (!outputPath.toString().endsWith(".jar")) {
			outputPath = Paths.get(outputPath + ".jar");
		}
		return outputPath;
	}
}

@Command(name = "jlink", description = "Exports a minimized JDK distribution")
class ExportJlink extends BaseExportCommand {

	@CommandLine.Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the jlink command")
	public List<String> params = new ArrayList<>();

	@Override
	protected ProjectBuilder createProjectBuilder(ExportMixin exportMixin) {
		ProjectBuilder pb = super.createProjectBuilder(exportMixin);
		pb.moduleName("");
		return pb;
	}

	@Override
	int apply(BuildContext ctx) throws IOException {
		Project prj = ctx.getProject();
		List<ArtifactInfo> artifacts = ctx.resolveClassPath().getArtifacts();
		List<ArtifactInfo> nonMods = artifacts
			.stream()
			.filter(a -> !ModuleUtil.isModule(a.getFile()))
			.collect(Collectors.toList());
		if (!nonMods.isEmpty()) {
			String lst = nonMods
				.stream()
				.map(a -> a.getCoordinate().toCanonicalForm())
				.collect(Collectors.joining(", "));
			Util.warnMsg("Export might fail because some dependencies are not full modules: " + lst);
		}

		Path outputPath = getJlinkOutputPath();
		String relativeOP;
		if (Util.getCwd().relativize(outputPath).isAbsolute()) {
			relativeOP = outputPath.toString();
		} else {
			relativeOP = "." + File.separator + Util.getCwd().relativize(outputPath);
		}
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.errorMsg("Cannot export as " + relativeOP + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		String jlinkCmd = JavaUtil.resolveInJavaHome("jlink", prj.projectJdk());
		String modMain = ModuleUtil.getModuleMain(prj);
		List<String> cps = artifacts.stream().map(a -> a.getFile().toString()).collect(Collectors.toList());
		List<String> cp = new ArrayList<>(artifacts.size() + 1);
		if (ctx.getJarFile() != null && !cps.contains(ctx.getJarFile().toString())) {
			cp.add(ctx.getJarFile().toString());
		}
		cp.addAll(cps);

		List<String> args = new ArrayList<>();
		args.add(jlinkCmd);
		args.add("--output");
		args.add(outputPath.toString());
		args.add("-p");
		args.add(String.join(CP_SEPARATOR, cp));
		args.add("--add-modules");
		args.add(ModuleUtil.getModuleName(prj));
		String launcherName = null;
		if (modMain != null) {
			launcherName = CatalogUtil.nameFromRef(exportMixin.scriptMixin.scriptOrFile);
			args.add("--launcher");
			args.add(launcherName + "=" + modMain);
		} else {
			Util.warnMsg(
					"No launcher will be generated because no main class is defined. Use '--main' to set a main class");
		}
		args.addAll(params);

		Util.verboseMsg("Run: " + String.join(" ", args));
		String out = Util.runCommand(args.toArray(new String[] {}));
		if (out == null) {
			Util.errorMsg("Unable to export Jdk distribution.");
			return EXIT_GENERIC_ERROR;
		}

		Util.infoMsg("Exported to " + relativeOP);
		if (modMain != null) {
			Util.infoMsg("A launcher has been created which you can run using: " + relativeOP + "/bin/" + launcherName);
		}
		return EXIT_OK;
	}

	private Path getJlinkOutputPath() {
		Path outputPath = exportMixin.getOutputPath("-jlink");
		return outputPath;
	}
}

abstract class BaseExportProject extends BaseExportCommand {

	@CommandLine.Option(names = { "--group", "-g" }, description = "The group ID to use for the exported project.")
	String group;

	@CommandLine.Option(names = { "--artifact",
			"-a" }, description = "The artifact ID to use for the exported project.")
	String artifact;

	@CommandLine.Option(names = { "--version", "-v" }, description = "The version to use for the exported project.")
	String version;

	protected EnumSet<Source.Type> supportedSourceTypes = EnumSet.of(Source.Type.java, Source.Type.groovy,
			Source.Type.kotlin);

	@Override
	int apply(BuildContext ctx) throws IOException {
		Path projectDir = exportMixin.getOutputPath("");
		if (projectDir.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(projectDir, false);
			} else {
				Util.errorMsg("Cannot export as " + projectDir + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		Project prj = ctx.getProject();
		if (prj.isJar() || prj.getMainSourceSet().getSources().isEmpty()) {
			Util.errorMsg("You can only export source files");
			return EXIT_INVALID_INPUT;
		}

		if (prj.getGav().isPresent()) {
			MavenCoordinate coord = MavenCoordinate.fromString(prj.getGav().get()).withVersion();
			if (group == null) {
				group = coord.getGroupId();
			}
			if (artifact == null) {
				artifact = coord.getArtifactId();
			}
			if (version == null) {
				version = coord.getVersion();
			}
		}

		if (group == null) {
			group = "org.example.project";
			info("No explicit group found, using " + group + " as fallback.");
		}
		artifact = artifact != null ? artifact
				: Util.getBaseName(Objects.requireNonNull(prj.getResourceRef().getFile()).getFileName().toString());
		version = version != null ? version : MavenCoordinate.DEFAULT_VERSION;

		createProjectForExport(ctx, projectDir);

		info("Exported as " + getType() + " project to " + projectDir);
		info("Export to " + getType() + " is a best-effort to try match JBang build.");
		info("If something can be improved please open issue at open an issue at https://github.com/jbangdev/jbang/issues with reproducer.");
		return EXIT_OK;
	}

	private void createProjectForExport(BuildContext ctx, Path projectDir) throws IOException {
		Project prj = ctx.getProject();
		Util.mkdirs(projectDir);

		// Sources
		String srcFolder = ctx.getProject().getMainSource().getType().sourceFolder;
		Path srcJavaDir = projectDir.resolve("src/main/" + srcFolder);
		for (ResourceRef sourceRef : prj.getMainSourceSet().getSources()) {
			copySource(sourceRef, srcJavaDir);
		}

		// Resources
		Path srcResourcesDir = projectDir.resolve("src/main/resources");
		for (RefTarget ref : prj.getMainSourceSet().getResources()) {
			Path destFile = ref.to(srcResourcesDir);
			Util.mkdirs(destFile.getParent());
			Files.copy(Objects.requireNonNull(ref.getSource().getFile()).toAbsolutePath(), destFile);
		}

		// Build file
		renderBuildFile(ctx, projectDir);

		// Wrapper files
		installWrapperFiles(ctx, projectDir);
	}

	private Path copySource(ResourceRef sourceRef, Path srcJavaDir)
			throws IOException {
		Path srcFile = Objects.requireNonNull(sourceRef.getFile());
		Source src = Source.forResourceRef(sourceRef, Function.identity());
		String fileName = Util.unkebabify(srcFile.getFileName().toString());
		Path destFile;
		if (src.getJavaPackage().isPresent()) {
			Path packageDir = srcJavaDir.resolve(src.getJavaPackage().get().replace(".", File.separator));
			Util.mkdirs(packageDir);
			destFile = packageDir.resolve(fileName);
			Files.copy(srcFile, destFile);
		} else {
			destFile = srcJavaDir.resolve(fileName);
			Files.createDirectories(destFile.getParent());
			Files.copy(srcFile, destFile);
		}
		return destFile;
	}

	abstract String getType();

	abstract void renderBuildFile(BuildContext ctx, Path projectDir) throws IOException;

	abstract void installWrapperFiles(BuildContext ctx, Path projectDir) throws IOException;

	String getJavaVersion(Project prj, boolean minorVersionFor8) {
		if (prj.getJavaVersion() == null) {
			return null;
		}
		int javaVersion = JavaUtil.minRequestedVersion(prj.getJavaVersion());
		if (!minorVersionFor8) {
			return Integer.toString(javaVersion);
		}
		return javaVersion >= 9 ? Integer.toString(javaVersion) : "1." + javaVersion;
	}

	void copyWrapperFile(String srcPath, String dstPath, boolean execFlag) throws IOException {
		File dstFile = new File(dstPath);
		File dstDir = new File(dstFile.getParent());

		dstDir.mkdirs();
		InputStream ifs = this.getClass().getResourceAsStream(srcPath);
		Files.copy(ifs, Paths.get(dstPath), StandardCopyOption.REPLACE_EXISTING);

		if (Util.isWindows())
			return;

		if (execFlag) {
			Util.setExecutable(dstFile.toPath());
		}

	}

}

@Command(name = "gradle", description = "Exports a Gradle project")
class ExportGradleProject extends BaseExportProject {

	@Override
	String getType() {
		return "gradle";
	}

	@Override
	void renderBuildFile(BuildContext ctx, Path projectDir) throws IOException {
		Project prj = ctx.getProject();
		ResourceRef templateRef = ResourceRef.forResource("classpath:/export-build.qute.gradle");
		Path destination = projectDir.resolve("build.gradle");

		List<MavenRepo> repositories = prj.getRepositories();
		List<String> dependencies = prj.getMainSourceSet().getDependencies();
		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = dependencies.stream()
			.map(JitPackUtil::ensureGAV)
			.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(dependencies)
				&& repositories.stream().noneMatch(r -> DependencyUtil.REPO_JITPACK.equals(r.getUrl()))) {
			prj.addRepository(DependencyUtil.toMavenRepo(DependencyUtil.ALIAS_JITPACK));
		}

		TemplateEngine engine = TemplateEngine.instance();
		Template template = engine.getTemplate(templateRef);
		if (template == null)
			throw new ExitException(EXIT_INVALID_INPUT, "Could not locate template named: '" + templateRef + "'");
		Source.Type srcType = prj.getMainSource().getType();
		if (!supportedSourceTypes.contains(srcType)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unsupported source type: " + srcType.name());
		}
		String kotlinVersion = srcType == Source.Type.kotlin ? ((KotlinSource) prj.getMainSource()).getKotlinVersion()
				: "";
		String javaVersion = getJavaVersion(prj, false);
		String jvmArgs = gradleArgs(prj.getRuntimeOptions());
		String compilerArgs = gradleArgs(prj.getMainSourceSet().getCompileOptions());
		String result = template
			.data("group", group)
			.data("artifact", artifact)
			.data("version", version)
			.data("language", srcType.name())
			.data("javaVersion", javaVersion)
			.data("kotlinVersion", kotlinVersion)
			.data("description", prj.getDescription().orElse(""))
			.data("repositories", repositories.stream()
				.map(MavenRepo::getUrl)
				.filter(s -> !"".equals(s))
				.collect(Collectors.toList()))
			.data("gradledependencies", gradleify(depIds))
			.data("fullClassName", prj.getMainClass())
			.data("jvmArgs", jvmArgs)
			.data("enablePreview", prj.enablePreview() ? (javaVersion != null ? "true" : "") : "")
			.data("compilerArgs", compilerArgs)
			.render();
		Util.writeString(destination, result);
		Util.writeString(projectDir.resolve("settings.gradle"), "");
		Util.writeString(projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\n");
	}

	@Override
	void installWrapperFiles(BuildContext ctx, Path projectDir) throws IOException {
		String dir = projectDir.getFileName().toString();
		copyWrapperFile("/dist/gradle/gradlew", Paths.get(dir, "gradlew").toString(), true);
		copyWrapperFile("/dist/gradle/gradlew.bat", Paths.get(dir, "gradlew.bat").toString(), false);
		copyWrapperFile("/dist/gradle/gradle/wrapper/gradle-wrapper.properties",
				Paths.get(dir, "gradle", "wrapper", "gradle-wrapper.properties").toString(), false);
		copyWrapperFile("/dist/gradle/gradle/wrapper/gradle-wrapper-jar",
				Paths.get(dir, "gradle", "wrapper", "gradle-wrapper.jar").toString(), false);
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

	private String gradleArgs(List<String> options) {
		StringBuilder args = new StringBuilder();
		for (String arg : options) {
			if (args.length() > 0) {
				args.append(", ");
			}
			args.append(String.format("'%s'", arg));
		}
		return args.toString();
	}
}

@Command(name = "maven", description = "Exports a Maven project")
class ExportMavenProject extends BaseExportProject {

	@Override
	String getType() {
		return "maven";
	}

	@Override
	void renderBuildFile(BuildContext ctx, Path projectDir) throws IOException {
		Project prj = ctx.getProject();
		ResourceRef templateRef = ResourceRef.forResource("classpath:/export-pom.qute.xml");
		Path destination = projectDir.resolve("pom.xml");

		List<MavenRepo> repositories = prj.getRepositories();
		List<String> dependencies = prj.getMainSourceSet().getDependencies();
		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = dependencies.stream()
			.map(JitPackUtil::ensureGAV)
			.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(dependencies)
				&& repositories.stream().noneMatch(r -> DependencyUtil.REPO_JITPACK.equals(r.getUrl()))) {
			prj.addRepository(DependencyUtil.toMavenRepo(DependencyUtil.ALIAS_JITPACK));
		}

		List<String> boms = depIds.stream().filter(d -> d.endsWith("@pom")).collect(Collectors.toList());
		depIds = depIds.stream().filter(d -> !d.endsWith("@pom")).collect(Collectors.toList());

		TemplateEngine engine = TemplateEngine.instance();
		Template template = engine.getTemplate(templateRef);
		if (template == null)
			throw new ExitException(EXIT_INVALID_INPUT, "Could not locate template named: '" + templateRef + "'");
		Source.Type srcType = prj.getMainSource().getType();
		if (!supportedSourceTypes.contains(srcType)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unsupported source type: " + srcType.name());
		}
		String kotlinVersion = srcType == Source.Type.kotlin ? ((KotlinSource) prj.getMainSource()).getKotlinVersion()
				: "";
		Map<String, String> properties = new HashMap<>();
		if (srcType == Source.Type.kotlin) {
			properties.put("kotlin.version", kotlinVersion);
		}
		String javaVersion = getJavaVersion(prj, false);
		String result = template
			.data("group", group)
			.data("artifact", artifact)
			.data("version", version)
			.data("language", srcType.name())
			.data("javaVersion", javaVersion)
			.data("kotlinVersion", kotlinVersion)
			.data("description", prj.getDescription().orElse(""))
			.data("properties", properties)
			.data("repositories", repositories.stream()
				.filter(r -> !r.getUrl().isEmpty())
				.collect(Collectors.toList()))
			.data("boms", boms.stream()
				.map(MavenCoordinate::fromString)
				.collect(Collectors.toList()))
			.data("dependencies", depIds.stream()
				.map(MavenCoordinate::fromString)
				.collect(Collectors.toList()))
			.data("fullClassName", prj.getMainClass())
			.data("compilerArgs", prj.getMainSourceSet().getCompileOptions())
			.render();
		Util.writeString(destination, result);
	}

	@Override
	void installWrapperFiles(BuildContext ctx, Path projectDir) throws IOException {
		String dir = projectDir.getFileName().toString();
		copyWrapperFile("/dist/maven/mvnw", Paths.get(dir, "mvnw").toString(), true);
		copyWrapperFile("/dist/maven/mvnw.cmd", Paths.get(dir, "mvnw.cmd").toString(), false);
		copyWrapperFile("/dist/maven/.mvn/wrapper/maven-wrapper.properties",
				Paths.get(dir, ".mvn", "wrapper", "maven-wrapper.properties").toString(), false);
	}

}