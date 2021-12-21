package dev.jbang.source.resolvers;

import static dev.jbang.source.resolvers.StdinScriptResourceResolver.stringToResourceRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

public class MarkupResourceResolver implements ResourceResolver {
	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		// map script argument to script file
		File probe = null;
		try {
			probe = Util.getCwd().resolve(resource).normalize().toFile();
		} catch (InvalidPathException e) {
			// Ignore
		}

		if (probe != null && probe.canRead()) {
			String ext = "." + Util.extension(probe.getName());
			if (".md".equals(ext)) {
				String scriptText = null;
				try {
					scriptText = Util.readString(probe.toPath());
				} catch (IOException e) {
					throw new IllegalStateException("Cuold not readed markup file " + probe.toPath());
				}
				scriptText = new MarkdownTransform().transformMarkdown(scriptText);

				try {
					result = stringToResourceRef(resource, scriptText);
				} catch (IOException e) {
					throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE,
							"Could not cache script from markup file " + probe, e);
				}

			}
		}

		return result;
	}

	static class MarkdownTransform {
		Pattern fourspacesOrTab = Pattern.compile("^( {4}|\t)");
		Pattern javacodeblock = Pattern.compile("^```(java|jsh)$");
		Pattern bashcodeblock = Pattern.compile("^```(bash)$");
		Pattern othercodeblock = Pattern.compile("^```(.*)$");
		Pattern spaceOrTab = Pattern.compile("^( +|\t)");
		Pattern endcodeblock = Pattern.compile("^```$");

		static boolean match(Pattern p, String s) {
			return p.matcher(s).matches();
		}

		String transformMarkdown(String source) {
			List<String> output = new ArrayList<String>();
			String state = "root";
			boolean prevLineIsEmpty = true;
			for (String line : source.split("\n")) {
				switch (state) {
				case "root":
					if (match(fourspacesOrTab, line) && prevLineIsEmpty) {
						output.add(line);
						state = "tab";
					} else if (match(javacodeblock, line)) {
						output.add("");
						state = "java";
					} else if (match(othercodeblock, line)) {
						output.add("");
						state = "other";
					} else {
						prevLineIsEmpty = line.isEmpty();
						output.add("// " + line);
					}
					break;
				case "tab":
					if (match(spaceOrTab, line)) {
						output.add(line);
					} else if (line.isEmpty()) {
						output.add("");
					} else {
						output.add("// " + line);
						state = "root";
					}
					break;
				case "java":
					if (match(endcodeblock, line)) {
						output.add("");
						state = "root";
					} else {
						output.add(line);
					}
					break;
				case "other":
					if (match(endcodeblock, line)) {
						output.add("");
						state = "root";
					} else {
						output.add("// " + line);
					}
					break;
				}
			}
			return String.join("\n", output);
		}
	}
}
