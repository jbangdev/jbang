package dev.jbang.cli;

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
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.")
public class Export extends BaseBuildCommand {

	@CommandLine.Option(names = { "-O",
			"--output" }, description = "The name or path to use for the exported file. If not specified a name will be determined from the original source reference and export flags.")
	Path outputFile;

	@CommandLine.Option(names = { "--force",
	}, description = "Force export, i.e. overwrite exported file if already exists", defaultValue = "false")
	boolean force;

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	ExportStyle exportStyle = new ExportStyle();

	static class ExportStyle {
		@CommandLine.Option(names = "--local", description = "Export built jar as is")
		boolean local = true;
		@CommandLine.Option(names = "--portable", description = "Make portable and standalone jar")
		boolean portable;
		@CommandLine.Option(names = "--mavenrepo", description = "Export artifacts to be used in a maven repository")
		boolean mavenpublish;
	}

	enum Style {
		local {

			public int apply(Export export, Source src, RunContext ctx) throws IOException {

				Path outputPath = export.getFileOutputPath(ctx);
				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (export.nativeImage) {
					source = getImageName(source.toFile()).toPath();
				}

				if (outputPath.toFile().exists()) {
					if (export.force) {
						outputPath.toFile().delete();
					} else {
						export.warn("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
						return EXIT_INVALID_INPUT;
					}
				}

				Files.copy(source, outputPath);
				export.info("Exported to " + outputPath);
				return EXIT_OK;
			}
		},
		portable {
			@Override
			public int apply(Export export, Source src, RunContext ctx) throws IOException {

				Path outputPath = export.getFileOutputPath(ctx);

				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (export.nativeImage) {
					source = getImageName(source.toFile()).toPath();
				}

				if (outputPath.toFile().exists()) {
					if (export.force) {
						outputPath.toFile().delete();
					} else {
						export.warn("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
						return EXIT_INVALID_INPUT;
					}
				}

				File tempManifest;

				Files.copy(source, outputPath);
				if (!export.nativeImage) {
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
							export.javaVersion != null ? export.javaVersion : src.getJavaVersion())); // TODO locate it
																										// on path ?
					optionList.add("ufm");
					optionList.add(outputPath.toString());
					optionList.add(tempManifest.toString());
					// System.out.println("Executing " + optionList);
					export.info("Updating jar manifest");
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
				export.info("Exported to " + outputPath);
				return EXIT_OK;
			}
		},
		mavenPublish {
			@Override
			public int apply(Export export, Source src, RunContext ctx) throws IOException {

				Path outputPath = export.outputFile;

				if (outputPath == null) {
					outputPath = Settings.getLocalMavenRepo().toPath();
				}
				// Copy the JAR or native binary
				Path source = src.getJarFile().toPath();
				if (export.nativeImage) {
					source = getImageName(source.toFile()).toPath();
				}

				if (!outputPath.toFile().isDirectory()) {
					if (outputPath.toFile().exists()) {
						export.warn("Cannot export to maven publish as " + outputPath + " is not a directory.");
						return EXIT_INVALID_INPUT;
					}
					if (export.force) {
						outputPath.toFile().mkdirs();
					} else {
						export.warn("Cannot export as " + outputPath + " does not exist. Use --force to create.");
						return EXIT_INVALID_INPUT;
					}
				}

				String group = ctx.getProperties().getOrDefault("group", "g.a.v");

				if (group == null) {
					export.warn(
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
					if (export.force) {
						artifactFile.toFile().delete();
					} else {
						export.warn("Cannot export as " + artifactFile + " already exists. Use --force to overwrite.");
						return EXIT_INVALID_INPUT;
					}
				}
				export.info("Writing " + artifactFile);
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
					export.info("Writing " + pomPath);
					Util.writeString(pomPath, pomfile);

				}

				export.info("Exported to " + outputPath);
				return EXIT_OK;
			}
		};

		public abstract int apply(Export export, Source src, RunContext ctx) throws IOException;
	}

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(null, properties, dependencies, classpaths, forcejsh);
		Source src = Source.forResource(scriptOrFile, ctx);

		src = buildIfNeeded(src, ctx);

		Style style = Style.local;

		if (exportStyle.portable) {
			style = Style.portable;
		} else if (exportStyle.mavenpublish) {
			style = Style.mavenPublish;
		}
		return style.apply(this, src, ctx);
	}

	Path getFileOutputPath(RunContext ctx) {
		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = CatalogUtil.nameFromRef(ctx.getOriginalRef());
			if (nativeImage) {
				outName = getImageName(new File(outName)).getName();
			} else {
				outName += ".jar";
			}
			outputPath = Paths.get(outName);
		}
		outputPath = cwd.resolve(outputPath);
		return outputPath;
	}

	public static String removeFileExtension(String filename, boolean removeAllExtensions) {
		if (filename == null || filename.isEmpty()) {
			return filename;
		}

		String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
		return filename.replaceAll(extPattern, "");
	}
}
