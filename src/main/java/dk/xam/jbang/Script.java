package dk.xam.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Script {

	private String DEPS_COMMENT_PREFIX = "//DEPS ";

	private String DEPS_ANNOT_PREFIX = "@Grab(";
	private Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

	File backingFile;
	private String classpath;
	private String script;
	private String mainClass;
	private File jar;

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

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath() {
		if (classpath == null) {
			List<String> dependencies = collectDependencies();

			classpath = new DependencyUtil().resolveDependencies(dependencies, Collections.emptyList(), true);
		}
		if (jar != null) {
			return classpath + Settings.CP_SEPARATOR + jar.getAbsolutePath();
		}
		return classpath;
	}

	Stream<String> extractDependencies(String line) {
		if (line.startsWith(DEPS_COMMENT_PREFIX)) {
			return Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim);
		}

		if (line.contains(DEPS_ANNOT_PREFIX)) {
			Map<String, String> args = new HashMap<>();

			Matcher matcher = DEPS_ANNOT_PAIRS.matcher(line);
			while (matcher.find()) {
				args.put(matcher.group("key"), matcher.group("value"));
			}
			if (!args.isEmpty()) {
				StringBuffer sb = new StringBuffer();
				// groupId:artifactId:version[:classifier][@type]
				String gav = Arrays.asList(
						args.get("group"),
						args.get("module"),
						args.get("version"),
						args.get("classifier")).stream().filter(Objects::nonNull).collect(Collectors.joining(":"));
				if (args.containsKey("ext")) {
					gav = gav + "@" + args.get("ext");
				}
				return Stream.of(gav);
			} else {
				matcher = DEPS_ANNOT_SINGLE.matcher(line);
				if (matcher.find()) {
					return Stream.of(matcher.group("value"));
				}
			}
		}

		return Stream.of();
	}

	boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX) || line.contains(DEPS_ANNOT_PREFIX);
	}

	public Script setMainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public Script setJar(File outjar) {
		this.jar = outjar;
		return this;
	}

	public File getJar() {
		return jar;
	}

	public String getMainClass() {
		return mainClass;
	}
}
