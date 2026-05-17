package dev.jbang.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import dev.jbang.cli.completion.ScriptRefCompleter;
import dev.jbang.source.Source;

public class ScriptMixin {

	@OptionList(shortName = 's', name = "sources", valueSeparator = ',', description = "Add additional sources.")
	List<String> sources;

	@OptionList(name = "files", valueSeparator = ',', description = "Add additional files.")
	List<String> resources;

	@Option(shortName = 'T', name = "source-type", description = "Force input to be interpreted as the given type. Can be: java, jshell, groovy, kotlin, or markdown")
	Source.Type forceType;

	@Option(name = "jsh", hasValue = false, description = "Force input to be interpreted with jsh/jshell. Deprecated: use '--source-type jshell'")
	Boolean forceJsh;

	@Option(name = "catalog", description = "Path to catalog file to be used instead of the default")
	File catalog;

	@Argument(paramLabel = "scriptOrFile", index = "0", arity = "0..1", description = "A file or URL to a Java code file", completer = ScriptRefCompleter.class)
	String scriptOrFile;

	public Source.Type getForceType() {
		if (Boolean.TRUE.equals(forceJsh)) {
			return Source.Type.jshell;
		}
		return forceType;
	}

	public void validate() {
		if (scriptOrFile == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Missing required parameter: '<scriptOrFile>'");
		}
	}

	public void validate(boolean scriptRequired) {
		if (scriptRequired) {
			validate();
		}
	}

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (sources != null) {
			for (String s : sources) {
				opts.add("--sources");
				opts.add(s);
			}
		}
		if (resources != null) {
			for (String r : resources) {
				opts.add("--files");
				opts.add(r);
			}
		}
		if (getForceType() != null) {
			opts.add("--source-type");
			opts.add(getForceType().toString());
		}
		if (catalog != null) {
			opts.add("--catalog");
			opts.add(catalog.getAbsolutePath());
		}
		return opts;
	}
}
