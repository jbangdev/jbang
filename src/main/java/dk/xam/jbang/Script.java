package dk.xam.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Script {

	private String DEPS_COMMENT_PREFIX = "//DEPS ";

	File backingFile;

	private String script;

	Script(File backingFile, String content) throws FileNotFoundException {
		this.backingFile = backingFile;
		this.script = content;
	}

	Script(File backingFile) throws FileNotFoundException {
		this.backingFile = backingFile;
		Scanner sc = new Scanner(this.backingFile);
		sc.useDelimiter("\\Z");
		this.script = sc.next();
	}

	Script(String script) {
		this.backingFile = null;
		this.script = script;
	}

	public List<String> collectDependencies() {

		List<String> lines = Arrays.asList(script.split("\\r?\\n"));

		// Make sure that dependencies declarations are well formatted
		if (lines.stream().anyMatch(it -> it.startsWith("// DEPS"))) {
			throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
		}

		List<String> dependencies = lines.stream().filter(it -> isDependDeclare(it))
				.flatMap(it -> extractDependencies(it)).collect(Collectors.toList());

		return dependencies;
	}

	Stream<String> extractDependencies(String line) {
		if (line.startsWith(DEPS_COMMENT_PREFIX)) {
			return Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim);
		}

		return Stream.of();
	}

	boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX);
	}

}
