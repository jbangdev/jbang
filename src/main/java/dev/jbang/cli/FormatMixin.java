package dev.jbang.cli;

import picocli.CommandLine;

public class FormatMixin {

	public enum Format {
		text, json
	}

	@CommandLine.Option(names = {
			"--format" }, description = "Specify output format ('text' or 'json')")
	protected Format format;
}
