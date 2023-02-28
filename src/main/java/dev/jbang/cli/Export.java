package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import dev.jbang.Settings;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.", subcommands = { ExportPortable.class,
		ExportLocal.class, ExportMavenPublish.class, ExportNative.class, ExportFatjar.class, ExportJlink.class })
public class Export {
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
		prj.codeBuilder(ctx).build();
		return apply(prj, ctx);
	}

	abstract int apply(Project prj, BuildContext ctx) throws IOException;

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
						.compileOptions(exportMixin.buildMixin.compileOptions);
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
	int apply(Project prj, BuildContext ctx) throws IOException {
		// Copy the JAR
		Path source = ctx.getJarFile();
		Path outputPath = getJarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				outputPath.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}
		Files.copy(source, outputPath);

		// Update the JAR's MANIFEST.MF Class-Path to point to
		// its dependencies
		String newPath = prj.resolveClassPath().getManifestPath();
		if (!newPath.isEmpty()) {
			Util.infoMsg("Updating jar...");
			String javaVersion = exportMixin.buildMixin.javaVersion != null
					? exportMixin.buildMixin.javaVersion
					: prj.getJavaVersion();
			JarUtil.updateJar(outputPath, createManifest(newPath), prj.getMainClass(), javaVersion);
		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}
}

@Command(name = "portable", description = "Exports jar together with dependencies in way that makes it portable")
class ExportPortable extends BaseExportCommand {

	public static final String LIB = "lib";

	@Override
	int apply(Project prj, BuildContext ctx) throws IOException {
		// Copy the JAR
		Path source = ctx.getJarFile();
		Path outputPath = getJarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				outputPath.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		Files.copy(source, outputPath);
		List<ArtifactInfo> deps = prj.resolveClassPath().getArtifacts();
		if (!deps.isEmpty()) {
			// Copy dependencies to "./lib" dir
			Path libDir = outputPath.getParent().resolve(LIB);
			Util.mkdirs(libDir);
			StringBuilder newPath = new StringBuilder();
			for (ArtifactInfo dep : deps) {
				Files.copy(dep.getFile(), libDir.resolve(dep.getFile().getFileName()));
				newPath.append(" " + LIB + "/" + dep.getFile().getFileName());
			}

			Util.infoMsg("Updating jar...");
			String javaVersion = exportMixin.buildMixin.javaVersion != null
					? exportMixin.buildMixin.javaVersion
					: prj.getJavaVersion();
			JarUtil.updateJar(outputPath, createManifest(newPath.toString()), prj.getMainClass(), javaVersion);
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
	int apply(Project prj, BuildContext ctx) throws IOException {
		Path outputPath = exportMixin.outputFile;

		if (outputPath == null) {
			outputPath = Settings.getLocalMavenRepo();
		}
		// Copy the JAR
		Path source = ctx.getJarFile();

		if (!outputPath.toFile().isDirectory()) {
			if (outputPath.toFile().exists()) {
				Util.warnMsg("Cannot export as maven repository as " + outputPath + " is not a directory.");
				return EXIT_INVALID_INPUT;
			}
			if (exportMixin.force) {
				outputPath.toFile().mkdirs();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " does not exist. Use --force to create.");
				return EXIT_INVALID_INPUT;
			}
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
			Util.warnMsg(
					"Cannot export as maven repository as no group specified. Add --group=<group id> and run again.");
			return EXIT_INVALID_INPUT;
		}
		Path groupdir = outputPath.resolve(Paths.get(group.replace(".", "/")));

		artifact = artifact != null ? artifact
				: Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
		Path artifactDir = groupdir.resolve(artifact);

		version = version != null ? version : MavenCoordinate.DEFAULT_VERSION;
		Path versionDir = artifactDir.resolve(version);

		String suffix = source	.getFileName()
								.toString()
								.substring(source.getFileName().toString().lastIndexOf("."));
		Path artifactFile = versionDir.resolve(artifact + "-" + version + suffix);

		artifactFile.getParent().toFile().mkdirs();

		if (artifactFile.toFile().exists()) {
			if (exportMixin.force) {
				artifactFile.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + artifactFile + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}
		Util.infoMsg("Writing " + artifactFile);
		Files.copy(source, artifactFile);

		// generate pom.xml ... if jar could technically just copy from the jar ...but
		// not possible when native thus for now just regenerate it
		Template pomTemplate = TemplateEngine	.instance()
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
										.data("dependencies", prj.resolveClassPath().getArtifacts())
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
	int apply(Project prj, BuildContext ctx) throws IOException {
		// Copy the native binary
		Path source = ctx.getNativeImageFile();
		Path outputPath = getNativeOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				outputPath.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
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
	int apply(Project prj, BuildContext ctx) throws IOException {
		// Copy the native binary
		Path source = ctx.getJarFile();
		Path outputPath = getFatjarOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				outputPath.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		List<ArtifactInfo> deps = prj.resolveClassPath().getArtifacts();
		if (!deps.isEmpty()) {
			// Extract main jar and all dependencies to a temp dir
			Path tmpDir = Files.createTempDirectory("fatjar");
			try {
				Util.verboseMsg("Unpacking main jar: " + source);
				UnpackUtil.unzip(source, tmpDir, false, null, ExportFatjar::handleExistingFile);
				for (ArtifactInfo dep : deps) {
					Util.verboseMsg("Unpacking artifact: " + dep);
					UnpackUtil.unzip(dep.getFile(), tmpDir, false, null, ExportFatjar::handleExistingFile);
				}
				JarUtil.createJar(outputPath, tmpDir, null, prj.getMainClass(), prj.getJavaVersion());
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

	public static void handleExistingFile(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile) throws IOException {
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

	@Override
	protected ProjectBuilder createProjectBuilder(ExportMixin exportMixin) {
		ProjectBuilder pb = super.createProjectBuilder(exportMixin);
		pb.moduleName("");
		return pb;
	}

	@Override
	int apply(Project prj, BuildContext ctx) throws IOException {
		Path outputPath = getJlinkOutputPath();
		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				Util.deletePath(outputPath, false);
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		String jlinkCmd = JavaUtil.resolveInJavaHome("jlink", null);
		String modMain = ModuleUtil.getModuleMain(prj);
		List<String> cps = prj.resolveClassPath().getClassPaths();
		List<String> cp = new ArrayList<>(cps.size() + 1);
		if (ctx.getJarFile() != null && !cps.contains(ctx.getJarFile())) {
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
		if (modMain != null) {
			String name = CatalogUtil.nameFromRef(exportMixin.scriptMixin.scriptOrFile);
			args.add("--launcher");
			args.add(name + "=" + modMain);
		} else {
			info("No launcher will be generated because no main class is defined. Use '--main' to set a main class");
		}

		Util.verboseMsg("Run: " + String.join(" ", args));
		String out = Util.runCommand(args.toArray(new String[] {}));
		if (out == null) {
			Util.warnMsg("Unable to export Jdk distribution.");
			return EXIT_GENERIC_ERROR;
		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}

	private Path getJlinkOutputPath() {
		Path outputPath = exportMixin.getOutputPath("-jlink");
		return outputPath;
	}
}
