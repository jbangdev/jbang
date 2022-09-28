package dev.jbang.cli;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import dev.jbang.Settings;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.source.Project;
import dev.jbang.source.RunContext;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.", subcommands = { ExportPortable.class,
		ExportLocal.class, ExportMavenPublish.class, ExportNative.class })
public class Export {
}

abstract class BaseExportCommand extends BaseCommand {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	protected void updateJarManifest(Path jar, Path manifest, String javaVersion) throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInJavaHome("jar", javaVersion));
		// locate it on path ?
		optionList.add("ufm");
		optionList.add(jar.toString());
		optionList.add(manifest.toString());
		// System.out.println("Executing " + optionList);
		Util.infoMsg("Updating jar manifest");
		Util.verboseMsg(String.join(" ", optionList));
		// no inheritIO as jar complains unnecessarily about duplicate manifest entries.
		Process process = new ProcessBuilder(optionList).start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error updating jar manifest");
		}
	}

	protected Path createManifest(String newPath) throws IOException {
		// Create a MANIFEST.MF file with a Class-Path option
		Path tempManifest = Files.createTempFile("MANIFEST", "MF");
		Manifest mf = new Manifest();
		// without MANIFEST_VERSION nothing is saved.
		mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mf.getMainAttributes().putValue(Attributes.Name.CLASS_PATH.toString(), newPath);
		try (OutputStream out = Files.newOutputStream(tempManifest)) {
			mf.write(out);
		}
		return tempManifest;
	}

	@Override
	public Integer doCall() throws IOException {
		exportMixin.validate();
		RunContext ctx = getRunContext(exportMixin);
		Project prj = ctx.forResource(exportMixin.scriptMixin.scriptOrFile).builder().build();
		return apply(prj, ctx);
	}

	abstract int apply(Project prj, RunContext ctx) throws IOException;

	protected RunContext getRunContext(ExportMixin exportMixin) {
		RunContext ctx = new RunContext();
		ctx.setProperties(exportMixin.dependencyInfoMixin.getProperties());
		ctx.setAdditionalDependencies(exportMixin.dependencyInfoMixin.getDependencies());
		ctx.setAdditionalRepositories(exportMixin.dependencyInfoMixin.getRepositories());
		ctx.setAdditionalClasspaths(exportMixin.dependencyInfoMixin.getClasspaths());
		ctx.setAdditionalSources(exportMixin.scriptMixin.sources);
		ctx.setAdditionalResources(exportMixin.scriptMixin.resources);
		ctx.setForceType(exportMixin.scriptMixin.forceType);
		ctx.setCatalog(exportMixin.scriptMixin.catalog);
		ctx.setJavaVersion(exportMixin.buildMixin.javaVersion);
		ctx.setMainClass(exportMixin.buildMixin.main);
		return ctx;
	}
}

@Command(name = "local", description = "Exports jar with classpath referring to local machine dependent locations")
class ExportLocal extends BaseExportCommand {

	@Override
	int apply(Project prj, RunContext ctx) throws IOException {
		// Copy the JAR
		Path source = prj.getJarFile();
		Path outputPath = exportMixin.getJarOutputPath();
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
			Path tempManifest = createManifest(newPath);

			String javaVersion = exportMixin.buildMixin.javaVersion != null
					? exportMixin.buildMixin.javaVersion
					: prj.getJavaVersion();
			updateJarManifest(outputPath, tempManifest, javaVersion);
		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}
}

@Command(name = "portable", description = "Exports jar together with dependencies in way that makes it portable")
class ExportPortable extends BaseExportCommand {

	public static final String LIB = "lib";

	@Override
	int apply(Project prj, RunContext ctx) throws IOException {
		// Copy the JAR
		Path source = prj.getJarFile();
		Path outputPath = exportMixin.getJarOutputPath();
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

			Path tempManifest = createManifest(newPath.toString());

			String javaVersion = exportMixin.buildMixin.javaVersion != null
					? exportMixin.buildMixin.javaVersion
					: prj.getJavaVersion();
			updateJarManifest(outputPath, tempManifest, javaVersion);
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
	int apply(Project prj, RunContext ctx) throws IOException {
		Path outputPath = exportMixin.outputFile;

		if (outputPath == null) {
			outputPath = Settings.getLocalMavenRepo();
		}
		// Copy the JAR
		Path source = prj.getJarFile();

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
		Template pomTemplate = TemplateEngine.instance().getTemplate("pom.qute.xml");

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
	int apply(Project prj, RunContext ctx) throws IOException {
		// Copy the native binary
		Path source = prj.getNativeImageFile();
		Path outputPath = exportMixin.getNativeOutputPath();
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
	protected RunContext getRunContext(ExportMixin exportMixin) {
		RunContext ctx = super.getRunContext(exportMixin);
		ctx.setNativeImage(true);
		return ctx;
	}
}
