package dev.jbang.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
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
			"--force" }, description = "Force re-installation")
	boolean force;

	@Override
	public Integer doCall() throws IOException {
		dev.jbang.catalog.Template tpl = dev.jbang.catalog.Template.get(null, initTemplate);
		if (tpl == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Could not find init template named: " + initTemplate);
		}

		List<Ref> refs = tpl.fileRefs
										.stream()
										.map(ref -> tpl.resolve(null, ref))
										.map(ref -> Ref.fromReference(tpl.catalog.catalogFile.toString(), ref))
										.collect(Collectors.toList());

		Path outFile = Util.getCwd().resolve(scriptOrFile);
		Path outDir = outFile.getParent();

		if (!force) {
			// Check if any of the files already exist
			boolean first = true;
			for (Ref ref : refs) {
				if (Files.exists(refToPath(outFile, ref, first))) {
					warn("File " + ref.getRef() + " already exists. Will not initialize.");
					return EXIT_GENERIC_ERROR;
				}
				first = false;
			}
		}

		try {
			boolean first = true;
			for (Ref ref : refs) {
				if (ref.getRef().endsWith(".qute")) {
					// TODO fix outFile path handling
					Path out = refToPath(outFile, ref, first);
					renderQuteTemplate(out, ref.getRef());
				} else {
					ref.copy(outDir);
				}
				first = false;
			}
		} catch (IOException e) {
			// Clean up any files we already created
			boolean first = true;
			for (Ref ref : refs) {
				Util.deletePath(refToPath(outFile, ref, true), true);
				first = false;
			}
		}

		info("File initialized. You can now run it with 'jbang " + scriptOrFile
				+ "' or edit it using 'jbang edit --open=[editor] "
				+ scriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
				+ knowneditors[new Random().nextInt(knowneditors.length)] + "'");

		return EXIT_OK;
	}

	private Path refToPath(Path outFile, Ref ref, boolean first) {
		if (first) {
			return outFile;
		} else {
			return ref.to(outFile.getParent());
		}
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
