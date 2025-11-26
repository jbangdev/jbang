package dev.jbang.cli;

import static dev.jbang.util.Util.entry;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import dev.jbang.ai.AIProvider;
import dev.jbang.ai.AIProviderFactory;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.source.RefTarget;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;

@CommandLine.Command(name = "init", description = "Initialize a script.")
public class Init extends BaseCommand {

	@CommandLine.Mixin
	BuildMixin buildMixin;

	// @CommandLine.Mixin
	@ArgGroup(heading = "AI Options for init with a prompt:\n", exclusive = false)
	AIOptions aiOptions = new AIOptions();

	@CommandLine.Option(names = { "--template",
			"-t" }, description = "Init script with a java class useful for scripting")
	public String initTemplate;

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwrite of existing files")
	public boolean force;

	@CommandLine.Option(names = { "--edit" }, description = "Open editor on the generated file(s)")
	public boolean edit;

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	public Map<String, Object> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"--deps" }, converter = CommaSeparatedConverter.class, description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;
	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the generation")
	List<String> params = new ArrayList<>();

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

		String baseName = Util.getBaseName(Paths.get(scriptOrFile).getFileName().toString());
		String extension = Util.extension(scriptOrFile);

		properties.put("scriptref", scriptOrFile);
		properties.put("baseName", baseName);
		properties.put("dependencies", dependencies);

		int reqVersion = buildMixin.javaVersion != null ? JavaUtil.minRequestedVersion(buildMixin.javaVersion)
				: JavaUtil.getCurrentMajorJavaVersion();

		properties.put("requestedJavaVersion", buildMixin.javaVersion);
		properties.put("javaVersion", reqVersion);
		properties.put("compactSourceFiles", reqVersion >= 25);
		// properties.put("magiccontent", "//no gpt response. make sure you ran with
		// --preview and OPENAI_API_KEY set");

		List<RefTarget> refTargets = tpl.fileRefs.entrySet()
			.stream()
			.map(e -> entry(
					resolveBaseName(e.getKey(), e.getValue(), outName),
					tpl.resolve(e.getValue())))
			.map(e -> RefTarget.create(
					e.getValue(),
					e.getKey(),
					ResourceResolver.combined(tpl.catalog.catalogRef, ResourceResolver.forResources())))
			.collect(Collectors.toList());

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

		if (!params.isEmpty()) {

			AIProvider provider = new AIProviderFactory(aiOptions.provider, aiOptions.apiKey,
					aiOptions.endpoint,
					aiOptions.model)
				.createProvider();
			if (provider != null) {
				try {
					Util.infoMsg("JBang AI activated, using " + provider.getName() + ":" + provider.getModel()
							+ " for init. Have a bit of patience - Ctrl+C to abort.");
					String response = provider.generateCode(baseName, extension, String.join(" ", params),
							"" + reqVersion);
					// sometimes gpt adds a markdown ```java block so lets remove all lines starting
					// with ``` in the output.
					response = response.replaceAll("(?m)^```.*(?:\r?\n|$)", "");
					properties.put("magiccontent", response);
				} catch (IllegalStateException | IOException e) {
					Util.errorMsg(
							"Failed to generate code with " + provider.getName(), e);
					return EXIT_INTERNAL_ERROR;
				}
			} else {
				Util.warnMsg(
						"JBang AI activated, but no AI provider or API key found. Will use normal jbang init.");
			}
		}

		applyTemplateProperties(tpl);

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
		if (edit) {
			info("File initialized. Opening editor for you. You can also now run it with 'jbang "
					+ renderedScriptOrFile);
			// TODO: quick hack that gets the job of opening editor done; but really should
			// make a isolated api to open editor instead of invoking subcommand.
			// nice thing wit this is that it will honor you jbang config for edit
			// automatically.
			JBang.getCommandLine().execute("edit", renderedScriptOrFile);
		} else {
			info("File initialized. You can now run it with 'jbang " + renderedScriptOrFile
					+ "' or edit it using 'jbang edit --open=[editor] "
					+ renderedScriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
					+ Edit.knownEditors[new Random().nextInt(Edit.knownEditors.length)]
					+ "'. If your IDE supports JBang, you can edit the directory instead: 'jbang edit . "
					+ renderedScriptOrFile + "'. See https://jbang.dev/ide");
		}
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

	void renderQuteTemplate(Path outFile, ResourceRef templateRef, Map<String, Object> properties)
			throws IOException {
		Template template = TemplateEngine.instance().getTemplate(templateRef);
		if (template == null) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Could not find or load template: " + templateRef);
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
			Util.verboseMsg("Rendering template: " + templateRef + " with properties: " + properties);
			String result = templateWithData.render();

			writer.write(result);
			outFile.toFile().setExecutable(true);
		}
	}

	String getRenderedScriptOrFile(Map<String, String> fileRefs, List<RefTarget> refTargets, Path outDir,
			boolean absolute) {
		Optional<Map.Entry<String, String>> optionalFileRefEntry = fileRefs.entrySet()
			.stream()
			.filter(fileRef -> dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(
					fileRef.getKey())
				.find())
			.findFirst();
		if (optionalFileRefEntry.isPresent()) {
			Optional<RefTarget> optionalRefTarget = refTargets.stream()
				.filter(refTarget -> refTarget.getSource()
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
