package dev.jbang.cli;

import static dev.jbang.cli.Export.handle;
import static dev.jbang.source.builders.BaseBuilder.getImageName;
import static dev.jbang.util.JavaUtil.resolveInJavaHome;
import static dev.jbang.util.Util.downloadFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.source.Code;
import dev.jbang.source.RunContext;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.", subcommands = { ExportPortable.class,
		ExportLocal.class, ExportMavenPublish.class })
public class Export {

	static int handle(ExportMixin exportMixin,
			Exporter style) throws IOException {
		exportMixin.scriptMixin.validate();

		RunContext ctx = getRunContext(exportMixin);
		Code code = ctx.forResource(exportMixin.scriptMixin.scriptOrFile).builder(ctx).build();
		return style.apply(exportMixin, code, ctx);
	}

	static RunContext getRunContext(ExportMixin exportMixin) {
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
		ctx.setNativeImage(exportMixin.buildMixin.nativeImage);
		return ctx;
	}
}

interface Exporter {
	int apply(ExportMixin exportMixin, Code code, RunContext ctx) throws IOException;
}

@Command(name = "local", description = "Exports jar with classpath referring to local machine dependent locations")
class ExportLocal extends BaseCommand implements Exporter {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	public int apply(ExportMixin exportMixin, Code code, RunContext ctx) throws IOException {

		Path outputPath = exportMixin.getFileOutputPath();
		// Copy the JAR or native binary
		Path source = code.getJarFile();
		if (exportMixin.buildMixin.nativeImage) {
			source = getImageName(source);
		}

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
	public Integer doCall() throws IOException {
		return handle(exportMixin, this);
	}
}

@Command(name = "portable", description = "Exports jar together with dependencies in way that makes it portable")
class ExportPortable extends BaseCommand implements Exporter {

	public static final String LIB = "lib";

	@CommandLine.Mixin
	ExportMixin exportMixin;

	public int apply(ExportMixin exportMixin, Code code, RunContext ctx) throws IOException {

		Path outputPath = exportMixin.getFileOutputPath();

		// Copy the JAR or native binary
		Path source = code.getJarFile();
		if (exportMixin.buildMixin.nativeImage) {
			source = getImageName(source);
		}

		if (outputPath.toFile().exists()) {
			if (exportMixin.force) {
				outputPath.toFile().delete();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		File tempManifest;

		Files.copy(source, outputPath);
		if (!exportMixin.buildMixin.nativeImage) {
			try (JarFile jf = new JarFile(outputPath.toFile())) {
				String cp = jf.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
				String[] deps = cp == null ? new String[0] : cp.split(" ");
				File libDir = new File(outputPath.toFile().getParentFile(), LIB);
				if (deps.length > 0) {
					if (!libDir.exists()) {
						libDir.mkdirs();
					}
				}
				StringBuilder newPath = new StringBuilder();
				for (String dep : deps) {
					Path file = downloadFile(new File(dep).toURI().toString(), libDir);
					newPath.append(" " + LIB + "/" + file.toFile().getName());
				}

				Path tempDirectory = Files.createTempDirectory("jbang-export");
				tempManifest = new File(tempDirectory.toFile(), "MANIFEST.MF");
				Manifest mf = new Manifest();
				// without MANIFEST_VERSION nothing is saved.
				mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
				mf.getMainAttributes().putValue(Attributes.Name.CLASS_PATH.toString(), newPath.toString());

				mf.write(new FileOutputStream(tempManifest));
			}

			List<String> optionList = new ArrayList<>();
			optionList.add(resolveInJavaHome("jar",
					exportMixin.buildMixin.javaVersion != null
							? exportMixin.buildMixin.javaVersion
							: code.getJavaVersion())); // TODO
			// locate it on path ?
			optionList.add("ufm");
			optionList.add(outputPath.toString());
			optionList.add(tempManifest.toString());
			// System.out.println("Executing " + optionList);
			Util.infoMsg("Updating jar manifest");
			// no inheritIO as jar complains unnecessarily about dupilcate manifest entries.
			Process process = new ProcessBuilder(optionList).start();
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				throw new ExitException(1, e);
			}

			if (process.exitValue() != 0) {
				throw new ExitException(1, "Error during updating jar");
			}
		}
		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}

	@Override
	public Integer doCall() throws IOException {
		return handle(exportMixin, this);
	}
}

@Command(name = "mavenrepo", description = "Exports directory that can be used to publish as a maven repository")
class ExportMavenPublish extends BaseCommand implements Exporter {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	@CommandLine.Option(names = { "--group", "-g" }, description = "The group ID to use for the generated POM.")
	String group;

	@CommandLine.Option(names = { "--artifact", "-a" }, description = "The artifact ID to use for the generated POM.")
	String artifact;

	@CommandLine.Option(names = { "--version", "-v" }, description = "The version to use for the generated POM.")
	String version;

	public int apply(ExportMixin exportMixin, Code code, RunContext ctx) throws IOException {
		Path outputPath = exportMixin.outputFile;

		if (outputPath == null) {
			outputPath = Settings.getLocalMavenRepo();
		}
		// Copy the JAR or native binary
		Path source = code.getJarFile();
		if (exportMixin.buildMixin.nativeImage) {
			source = getImageName(source);
		}

		if (!outputPath.toFile().isDirectory()) {
			if (outputPath.toFile().exists()) {
				Util.warnMsg("Cannot export to maven publish as " + outputPath + " is not a directory.");
				return EXIT_INVALID_INPUT;
			}
			if (exportMixin.force) {
				outputPath.toFile().mkdirs();
			} else {
				Util.warnMsg("Cannot export as " + outputPath + " does not exist. Use --force to create.");
				return EXIT_INVALID_INPUT;
			}
		}

		if (code.getGav().isPresent()) {
			MavenCoordinate coord = DependencyUtil.depIdToArtifact(DependencyUtil.gavWithVersion(code.getGav().get()));
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
					"Cannot export to maven publish as no group specified. Add --group=<group id> and run again.");
			return EXIT_INVALID_INPUT;
		}
		Path groupdir = outputPath.resolve(Paths.get(group.replace(".", "/")));

		artifact = artifact != null ? artifact
				: Util.getBaseName(code.getResourceRef().getFile().getFileName().toString());
		Path artifactDir = groupdir.resolve(artifact);

		version = version != null ? version : "999-SNAPSHOT";
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
														code.getResourceRef().getFile().getFileName().toString()))
										.data("group", group)
										.data("artifact", artifact)
										.data("version", version)
										.data("description", code.getDescription().orElse(""))
										.data("dependencies", ctx.resolveClassPath(code).getArtifacts())
										.render();
			Util.infoMsg("Writing " + pomPath);
			Util.writeString(pomPath, pomfile);

		}

		Util.infoMsg("Exported to " + outputPath);
		return EXIT_OK;
	}

	@Override
	public Integer doCall() throws IOException {
		return handle(exportMixin, this);
	}
}