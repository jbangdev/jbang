package dev.jbang.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import dev.jbang.source.Ref;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Initialize a script.")
public class Init extends BaseScriptCommand {

	static String[] knowneditors = { "code", "eclipse", "idea", "vi", "emacs", "netbeans" };

	@CommandLine.Option(names = { "--template",
			"-t" }, description = "Init script with a java class useful for scripting", defaultValue = "hello")
	String initTemplate;

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwrite of existing files")
	boolean force;

	@Override
	public Integer doCall() throws IOException {
		dev.jbang.catalog.Template tpl = dev.jbang.catalog.Template.get(null, initTemplate);
		if (tpl == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Could not find init template named: " + initTemplate);
		}

		Path outFile = Util.getCwd().resolve(scriptOrFile);
		Path outDir = outFile.getParent();
		String outName = outFile.getFileName().toString();

		List<Ref> refs = tpl.fileRefs	.entrySet()
										.stream()
										.map(e -> new AbstractMap.SimpleEntry<>(
												resolveBaseName(e.getKey(), e.getValue(), outName),
												tpl.resolve(null, e.getValue())))
										.map(e -> Ref.fromReference(tpl.catalog.catalogFile.toString(), e.getValue(),
												e.getKey()))
										.collect(Collectors.toList());

		if (!force) {
			// Check if any of the files already exist
			for (Ref ref : refs) {
				if (Files.exists(ref.to(outDir))) {
					warn("File " + ref.getRef() + " already exists. Will not initialize.");
					return EXIT_GENERIC_ERROR;
				}
			}
		}

		try {
			for (Ref ref : refs) {
				if (ref.getRef().endsWith(".qute")) {
					// TODO fix outFile path handling
					Path out = ref.to(outDir);
					renderQuteTemplate(out, ref.getRef());
				} else {
					ref.copy(outDir);
				}
			}
		} catch (IOException e) {
			// Clean up any files we already created
			boolean first = true;
			for (Ref ref : refs) {
				Util.deletePath(ref.to(outDir), true);
				first = false;
			}
		}

		info("File initialized. You can now run it with 'jbang " + scriptOrFile
				+ "' or edit it using 'jbang edit --open=[editor] "
				+ scriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
				+ knowneditors[new Random().nextInt(knowneditors.length)] + "'");

		return EXIT_OK;
	}

	private String resolveBaseName(String refTarget, String refSource, String outName) {
		String baseName = base(outName);
		String outExt = extension(outName);
		String targetExt = extension(refTarget);
		if (targetExt.isEmpty()) {
			targetExt = refSource.endsWith(".qute") ? extension(base(refSource)) : extension(refSource);
		}
		if (!outExt.isEmpty() && !outExt.equals(targetExt)) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Expected file extension is: " + targetExt + ", but got: " + outExt);
		}
		Pattern fnp = Pattern.compile("\\{filename}", Pattern.CASE_INSENSITIVE);
		String result = fnp.matcher(refTarget).replaceAll(outName);
		Pattern bnp = Pattern.compile("\\{basename}", Pattern.CASE_INSENSITIVE);
		result = bnp.matcher(result).replaceAll(baseName);
		return result;
	}

	private String base(String name) {
		int p = name.lastIndexOf('.');
		return p > 0 ? name.substring(0, p) : name;
	}

	private String extension(String name) {
		int p = name.lastIndexOf('.');
		return p > 0 ? name.substring(p + 1) : "";
	}

	void renderQuteTemplate(Path outFile, String templatePath) throws IOException {
		Template helloTemplate = TemplateEngine.instance().getTemplate(templatePath);

		String basename = Util.getBaseName(outFile.getFileName().toString());
		if (!SourceVersion.isIdentifier(basename)) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"'" + basename + "' is not a valid class name in java. Remove the special characters");
		}

		try (BufferedWriter writer = Files.newBufferedWriter(outFile)) {
			String result = helloTemplate.data("baseName", basename).render();
			writer.write(result);
			outFile.toFile().setExecutable(true);
		}
	}
}
