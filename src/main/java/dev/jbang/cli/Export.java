package dev.jbang.cli;

import static dev.jbang.Util.downloadFile;

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

import dev.jbang.ExitException;
import dev.jbang.ExtendedScript;
import dev.jbang.Util;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.")
public class Export extends BaseBuildCommand {

	@CommandLine.Option(names = { "-O",
			"--output" }, description = "The name or path to use for the exported file. If not specified a name will be determined from the original source ref")
	Path outputFile;

	@CommandLine.Option(names = { "--force",
	}, description = "Force export, i.e. overwrite exported file if already exists", defaultValue = "false")
	boolean force;

	@CommandLine.Option(names = {
			"--portable" }, description = "Make portable and standalone jar")
	boolean portable;

	enum Style {
		local {

			public int apply(Export export, ExtendedScript script, Path outputPath) throws IOException {
				// Copy the JAR or native binary
				Path source = script.getJar().toPath();
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
			public int apply(Export export, ExtendedScript script, Path outputPath) throws IOException {
				// Copy the JAR or native binary
				Path source = script.getJar().toPath();
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
						StringBuffer newPath = new StringBuffer();
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

					List<String> optionList = new ArrayList<String>();
					optionList.add(resolveInJavaHome("jar",
							export.javaVersion != null ? export.javaVersion : script.javaVersion())); // TODO locate it
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
		};

		public abstract int apply(Export export, ExtendedScript script, Path outputPath) throws IOException;
	}

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = ExtendedScript.prepareScript(scriptOrFile, null, properties, dependencies, classpaths, fresh,
				forcejsh);

		if (script.needsJar()) {
			build(script);
		}

		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = AppInstall.chooseCommandName(script);
			if (nativeImage) {
				outName = getImageName(new File(outName)).getName();
			} else {
				outName += ".jar";
			}
			outputPath = Paths.get(outName);
		}
		outputPath = cwd.resolve(outputPath);

		Style style = portable ? Style.portable : Style.local;

		return style.apply(this, script, outputPath);
	}
}
