package dev.jbang.cli;

import static dev.jbang.util.Util.entry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import dev.jbang.catalog.TemplateProperty;
import dev.jbang.source.RefTarget;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.resolvers.SiblingResourceResolver;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Initialize a script.")
public class Init extends BaseCommand {

	@CommandLine.Option(names = { "--template",
			"-t" }, description = "Init script with a java class useful for scripting")
	public String initTemplate;

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwrite of existing files")
	public boolean force;

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	public Map<String, Object> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"--deps" }, converter = CommaSeparatedConverter.class, description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	public void requireScriptArgument() {
		if (scriptOrFile == null) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		dev.jbang.catalog.Template tpl = dev.jbang.catalog.Template.get(initTemplate);
		if (tpl == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Could not find init template named: " + initTemplate
							+ ". Try run with --fresh to get latest catalog updates.");
		}

		boolean absolute = new File(scriptOrFile).isAbsolute();
		Path outFile = Util.getCwd().resolve(scriptOrFile);
		Path outDir = outFile.getParent();
		String outName = outFile.getFileName().toString();

		properties.put("scriptref", scriptOrFile);
		properties.put("baseName", Util.getBaseName(Paths.get(scriptOrFile).getFileName().toString()));
		properties.put("dependencies", dependencies);

		List<RefTarget> refTargets = tpl.fileRefs	.entrySet()
													.stream()
													.map(e -> entry(
															resolveBaseName(e.getKey(), e.getValue(), outName),
															tpl.resolve(e.getValue())))
													.map(e -> RefTarget.create(
															e.getValue(),
															e.getKey(),
															new SiblingResourceResolver(tpl.catalog.catalogRef)))
													.collect(Collectors.toList());

		applyTemplateProperties(tpl);

		if (!force) {
			// Check if any of the files already exist
			for (RefTarget refTarget : refTargets) {
				Path target = refTarget.to(outDir);
				if (Files.exists(target)) {
					warn("File " + target + " already exists. Will not initialize.");
					return EXIT_GENERIC_ERROR;
				}
			}
		}

		try {
			for (RefTarget refTarget : refTargets) {
				if (refTarget.getSource().getOriginalResource().endsWith(".qute")) {
					// TODO fix outFile path handling
					Path out = refTarget.to(outDir);
					renderQuteTemplate(out, refTarget.getSource(), properties);
				} else {
					refTarget.copy(outDir);
				}
			}
		} catch (IOException e) {
			// Clean up any files we already created
			for (RefTarget refTarget : refTargets) {
				Util.deletePath(refTarget.to(outDir), true);
			}
		}

		String renderedScriptOrFile = getRenderedScriptOrFile(tpl.fileRefs, refTargets, outDir, absolute);
		info("File initialized. You can now run it with 'jbang " + renderedScriptOrFile
				+ "' or edit it using 'jbang edit --open=[editor] "
				+ renderedScriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
				+ Edit.knownEditors[new Random().nextInt(Edit.knownEditors.length)] + "'");

		return EXIT_OK;
	}

	private void applyTemplateProperties(dev.jbang.catalog.Template tpl) {
		if (tpl.properties != null) {
			for (Map.Entry<String, TemplateProperty> entry : tpl.properties.entrySet()) {
				if (entry.getValue().getDefaultValue() != null) {
					properties.putIfAbsent(entry.getKey(), entry.getValue().getDefaultValue());
				}
			}
		}
	}

	static Path resolveBaseName(String refTarget, String refSource, String outName) {
		String result = refTarget;
		if (dev.jbang.cli.Template.TPL_FILENAME_PATTERN.matcher(refTarget).find()
				|| dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(refTarget).find()) {
			String baseName = Util.base(outName);
			String outExt = Util.extension(outName);
			String targetExt = Util.extension(refTarget);
			if (targetExt.isEmpty()) {
				targetExt = refSource.endsWith(".qute") ? Util.extension(Util.base(refSource))
						: Util.extension(refSource);
			}
			if (!outExt.isEmpty() && !outExt.equals(targetExt)) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"Template expects " + targetExt + " extension, not " + outExt);
			}
			result = dev.jbang.cli.Template.TPL_FILENAME_PATTERN.matcher(result).replaceAll(outName);
			result = dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(result).replaceAll(baseName);
		}
		return Paths.get(result);
	}

	private void renderQuteTemplate(Path outFile, ResourceRef templateRef, Map<String, Object> properties)
			throws IOException {
		Util.verboseMsg("Rendering template " + templateRef.getOriginalResource() + " to " + outFile);
		renderQuteTemplate(outFile, templateRef.getFile().toAbsolutePath().toString(), properties);
	}

	void renderQuteTemplate(Path outFile, String templatePath) throws IOException {
		renderQuteTemplate(outFile, templatePath, new HashMap<>());
	}

	void renderQuteTemplate(Path outFile, String templatePath, Map<String, Object> properties) throws IOException {
		Template template = TemplateEngine.instance().getTemplate(templatePath);
		if (template == null) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Could not find or load template: " + templatePath);
		}

		if (outFile.toString().endsWith(".java")) {
			String basename = Util.getBaseName(outFile.getFileName().toString());
			if (!SourceVersion.isIdentifier(basename)) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"'" + basename + "' is not a valid class name in java. Remove the special characters");
			}
		}

		Files.createDirectories(outFile.getParent());
		try (BufferedWriter writer = Files.newBufferedWriter(outFile)) {
			TemplateInstance templateWithData = template.instance();
			properties.forEach(templateWithData::data);

			String result = templateWithData.render();

			writer.write(result);
			outFile.toFile().setExecutable(true);
		}
	}

	String getRenderedScriptOrFile(Map<String, String> fileRefs, List<RefTarget> refTargets, Path outDir,
			boolean absolute) {
		Optional<Map.Entry<String, String>> optionalFileRefEntry = fileRefs	.entrySet()
																			.stream()
																			.filter(fileRef -> dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(
																					fileRef.getKey()).find())
																			.findFirst();
		if (optionalFileRefEntry.isPresent()) {
			Optional<RefTarget> optionalRefTarget = refTargets	.stream()
																.filter(refTarget -> refTarget	.getSource()
																								.getOriginalResource()
																								.endsWith(
																										optionalFileRefEntry.get()
																															.getValue()))
																.findFirst();
			if (optionalRefTarget.isPresent()) {
				Path path = optionalRefTarget.get().to(outDir);
				if (absolute) {
					return path.toString();
				} else {
					return Util.getCwd().relativize(path).toString();
				}
			}
		}
		return scriptOrFile;
	}
}
