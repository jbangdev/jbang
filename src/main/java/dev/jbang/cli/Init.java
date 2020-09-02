package dev.jbang.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Initialize a script.")
public class Init extends BaseScriptCommand {

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
					+ "' or edit it using 'code `jbang edit " + scriptOrFile + "`'");
		}
		return 0;
	}

	String renderInitClass(File f, String template) {
		Template helloTemplate = Settings.getTemplateEngine().getTemplate("init-" + template + ".java.qute");

		if (helloTemplate == null) {
			throw new ExitException(1, "Could not find init template named: " + template);
		} else {
			return helloTemplate.data("baseName", Util.getBaseName(f.getName())).render();
		}
	}
}
