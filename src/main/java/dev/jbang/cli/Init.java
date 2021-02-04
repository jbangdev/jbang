package dev.jbang.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import javax.lang.model.SourceVersion;

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

	@Override
	public Integer doCall() throws IOException {
		File f = new File(scriptOrFile);
		if (f.exists()) {
			warn("File " + f + " already exists. Will not initialize.");
		} else {
			if (f.getParentFile() != null && !f.getParentFile().exists()) {
				f.getParentFile().mkdirs();
			}
			// Use try-with-resource to get auto-closeable writer instance
			try (BufferedWriter writer = Files.newBufferedWriter(f.toPath())) {
				String result = renderInitClass(f, initTemplate);
				writer.write(result);
				f.setExecutable(true);
			} catch (ExitException e) {
				f.delete(); // if template lookup fails we need to delete file to not end up with a empty
				// file.
				throw e;
			}

			info("File initialized. You can now run it with 'jbang " + scriptOrFile
					+ "' or edit it using 'jbang edit --open=[editor] "
					+ scriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
					+ knowneditors[new Random().nextInt(knowneditors.length)] + "'");
		}
		return EXIT_OK;
	}

	String renderInitClass(File f, String template) {
		Template helloTemplate = TemplateEngine.instance().getTemplate("init-" + template + ".java.qute");

		if (helloTemplate == null) {
			throw new ExitException(1, "Could not find init template named: " + template);
		} else {
			String basename = Util.getBaseName(f.getName());

			if (!SourceVersion.isIdentifier(basename)) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"'" + basename + "' is not a valid class name in java. Remove the special charcters");
			}

			return helloTemplate.data("baseName", basename).render();
		}
	}
}
