package dev.jbang.cli;

import static dev.jbang.cli.BaseBuildCommand.buildIfNeeded;
import static dev.jbang.cli.BaseBuildCommand.getImageName;
import static dev.jbang.cli.BaseBuildCommand.resolveInJavaHome;
import static dev.jbang.cli.BaseScriptCommand.enableInsecure;
import static dev.jbang.cli.Export.handle;
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

import dev.jbang.Settings;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.", subcommands = { ExportPortable.class,
		ExportLocal.class, ExportMavenPublish.class })
public class Export extends BaseCommand {

	public static String removeFileExtension(String filename, boolean removeAllExtensions) {
		if (filename == null || filename.isEmpty()) {
			return filename;
		}

		String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
		return filename.replaceAll(extPattern, "");
	}

	static int handle(ExportMixin exportMixin,
			Style style) throws IOException {
		if (exportMixin.insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(null, exportMixin.dependencyInfoMixin.getProperties(),
				exportMixin.dependencyInfoMixin.getDependencies(), exportMixin.dependencyInfoMixin.getClasspaths(),
				false);
		Source src = Source.forResource(exportMixin.scriptOrFile, ctx);

		src = buildIfNeeded(src, ctx);

		return style.apply(exportMixin, src, ctx);
	}

	@Override
	public Integer doCall() throws IOException {

		throw new RuntimeException("shouldn happen");
	}

	enum Style {
		local {
			public int apply(ExportMixin exportMixin, Source src, RunContext ctx) throws IOException {

				Path outputPath = exportMixin.getFileOutputPath(ctx);
				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (exportMixin.nativeImage) {
					source = getImageName(source.toFile()).toPath();
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
		},
		portable {
			@Override
			public int apply(ExportMixin exportMixin, Source src, RunContext ctx) throws IOException {

				Path outputPath = exportMixin.getFileOutputPath(ctx);

				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (exportMixin.nativeImage) {
					source = getImageName(source.toFile()).toPath();
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
				if (!exportMixin.nativeImage) {
					try (JarFile jf = new JarFile(outputPath.toFile())) {
						String cp = jf.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
						String[] jars = cp == null ? new String[0] : cp.split(" ");
						File libsdir = new File(outputPath.toFile().getParentFile(), "libs");
						if (jars.length > 0) {
							if (!libsdir.exists()) {
								libsdir.mkdirs();
							}
						}
						StringBuilder newPath = new StringBuilder();
						for (String jar : jars) {
							Path file = downloadFile(new File(jar).toURI().toString(), libsdir);
							newPath.append(" libs/" + file.toFile().getName());
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
							exportMixin.javaVersion != null ? exportMixin.javaVersion : src.getJavaVersion())); // TODO
																												// locate
																												// it
					// on path ?
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
		},
		mavenPublish {
			@Override
			public int apply(ExportMixin exportMixin, Source src, RunContext ctx) throws IOException {

				Path outputPath = exportMixin.outputFile;

				if (outputPath == null) {
					outputPath = Settings.getLocalMavenRepo().toPath();
				}
				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (exportMixin.nativeImage) {
					source = getImageName(source.toFile()).toPath();
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

				String group = ctx.getProperties().getOrDefault("group", "g.a.v");

				if (group == null) {
					Util.warnMsg(
							"Cannot export to maven publish as no group specified. Add -Dgroup=<group id> and run again.");
					return EXIT_INVALID_INPUT;

				}
				Path groupdir = outputPath.resolve(Paths.get(group.replace(".", "/")));

				String artifact = ctx	.getProperties()
										.getOrDefault("artifact",
												Util.getBaseName(src.getResourceRef().getFile().getName()));
				Path artifactDir = groupdir.resolve(artifact);

				String version = ctx.getProperties().getOrDefault("version", "999-SNAPSHOT");
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
														Util.getBaseName(src.getResourceRef().getFile().getName()))
												.data("group", group)
												.data("artifact", artifact)
												.data("version", version)
												.data("dependencies", ctx.getClassPath().getArtifacts())
												.render();
					Util.infoMsg("Writing " + pomPath);
					Util.writeString(pomPath, pomfile);

				}

				Util.infoMsg("Exported to " + outputPath);
				return EXIT_OK;
			}
		};

		public abstract int apply(ExportMixin exportMixin, Source src, RunContext ctx) throws IOException;
	}

	static class ExportStyle {
		@CommandLine.Option(names = "--local", description = "Export built jar as is")
		boolean local = true;
		@CommandLine.Option(names = "--portable", description = "Make portable and standalone jar")
		boolean portable;
		@CommandLine.Option(names = "--mavenrepo", description = "Export artifacts to be used in a maven repository")
		boolean mavenpublish;
	}
}

@Command(name = "local")
class ExportLocal extends BaseCommand {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	@Override
	public Integer doCall() throws IOException {
		return handle(exportMixin, Export.Style.local);
	}
}

@Command(name = "portable")

class ExportPortable extends BaseCommand {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	@Override
	public Integer doCall() throws IOException {
		return handle(exportMixin, Export.Style.portable);
	}
}

@Command(name = "mavenrepo")

class ExportMavenPublish extends BaseCommand {

	@CommandLine.Mixin
	ExportMixin exportMixin;

	@Override
	public Integer doCall() throws IOException {
		return handle(exportMixin, Export.Style.mavenPublish);
	}
}