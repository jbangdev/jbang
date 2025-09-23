package dev.jbang.source.sources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

public class MarkdownSource extends JshSource {

	protected MarkdownSource(ResourceRef ref, String script, Function<String, String> replaceProperties) {
		super(ref, script, replaceProperties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.markdown;
	}

	public static Source create(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		String scriptText = new MarkdownTransform().transformMarkdown(
				Util.readFileContent(resourceRef.getFile()));
		try {
			// this will cache the content in stdin cache which is not optimal but needed to
			// have the transformed script stored
			// separately from the possibly originally cached file.
			resourceRef = LiteralScriptResourceResolver.stringToResourceRef(resourceRef.getOriginalResource(),
					scriptText, null);
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE,
					"Could not cache script from markdown at " + resourceRef.getOriginalResource(), e);
		}
		return new MarkdownSource(resourceRef,
				scriptText,
				replaceProperties);
	}

	static class MarkdownTransform {
		static final Pattern fourspacesOrTab = Pattern.compile("^( {4}|\t)");
		static final Pattern javacodeblock = Pattern.compile("^```(java|jsh|jshelllanguage)$");
		static final Pattern othercodeblock = Pattern.compile("^```(.*)$");
		static final Pattern spaceOrTab = Pattern.compile("^( +|\t)");
		static final Pattern endcodeblock = Pattern.compile("^```$");

		static boolean match(Pattern p, String s) {
			return p.matcher(s).matches();
		}

		public String transformMarkdown(String source) {
			List<String> output = new ArrayList<>();
			String state = "root";
			boolean prevLineIsEmpty = true;
			for (String line : source.split("\\R")) {
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
