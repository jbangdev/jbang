package dev.jbang.cli;

import java.io.File;
import java.util.List;

import dev.jbang.source.Source;

import picocli.CommandLine;

public class ScriptMixin {
	@CommandLine.Option(names = { "-s",
			"--sources" }, converter = CommaSeparatedConverter.class, description = "Add additional sources.")
	List<String> sources;

	@CommandLine.Option(names = {
			"--files" }, converter = CommaSeparatedConverter.class, description = "Add additional files.")
	List<String> resources;

	@CommandLine.Option(names = {
			"--source-type" }, description = "Force input to be interpreted as the given type. Can be: java, jshell, groovy, kotlin, or markdown")
	Source.Type forceType;

	@CommandLine.Option(names = {
			"--jsh" }, description = "Force input to be interpreted with jsh/jshell. Deprecated: use '--source-type jshell'")
	void setForcejsh(boolean forceJsh) {
		forceType = forceJsh ? Source.Type.jshell : null;
	}

	@CommandLine.Option(names = { "--catalog" }, description = "Path to catalog file to be used instead of the default")
	File catalog;

	@CommandLine.Parameters(index = "0", arity = "0..1", description = "A reference to a source file")
	String scriptOrFile;

	public void validate() {
		if (scriptOrFile == null) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

}
