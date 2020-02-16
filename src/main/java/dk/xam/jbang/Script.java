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

	/**
	 * the file that contains the code that will back the actual compile/execution.
	 * Might have been altered to be runnable; i.e. stripped out !# before launch.
	 */
	File backingFile;
	/**
	 * The original file, it might or might not be same as used as backingFile.
	 */
	private File originalFile;

	private ModularClassPath classpath;
	private String script;
	private String mainClass;
	private File jar;
	List<String> lines;

	Script(File backingFile, String content) throws FileNotFoundException {
		this.backingFile = backingFile;
		this.script = content;
	}

	Script(File backingFile) throws FileNotFoundException {
		this.backingFile = backingFile;
		try (Scanner sc = new Scanner(this.backingFile)) {
			sc.useDelimiter("\\Z");
			this.script = sc.next();
		}
	}

	Script(String script) {
		this.backingFile = null;
		this.script = script;
	}

	private List<String> getLines() {
		if (lines == null) {
			lines = Arrays.asList(script.split("\\r?\\n"));
		}
		return lines;
	}

	public List<String> collectDependencies() {
		// early/eager init to property resolution will work.
		new Detector().detect(new Properties(), Collections.emptyList());

		// Make sure that dependencies declarations are well formatted
		if (getLines().stream().anyMatch(it -> it.startsWith("// DEPS"))) {
			throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
		}

		List<String> dependencies = getLines()	.stream()
												.filter(it -> isDependDeclare(it))
												.flatMap(it -> extractDependencies(it))
												.map(it -> PropertiesValueResolver.replaceProperties(it))
												.collect(Collectors.toList());

		return dependencies;
	}

	// https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
	List<String> quotedStringToList(String subjectString) {
		List<String> matchList = new ArrayList<String>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(subjectString);
		while (regexMatcher.find()) {
			if (regexMatcher.group(1) != null) {
				// Add double-quoted string without the quotes
				matchList.add(regexMatcher.group(1));
			} else if (regexMatcher.group(2) != null) {
				// Add single-quoted string without the quotes
				matchList.add(regexMatcher.group(2));
			} else {
				// Add unquoted word
				matchList.add(regexMatcher.group());
			}
		}
		return matchList;
	}

	private List<String> collectOptions(String prefix) {
		String joptsPrefix = "//" + prefix;

		List<String> lines = getLines();

		List<String> javaOptions = lines.stream()
										.filter(it -> it.startsWith(joptsPrefix))
										.map(it -> it.replaceFirst(joptsPrefix, "").trim())
										.collect(Collectors.toList());

		String envOptions = System.getenv("JBANG_" + prefix);
		if (envOptions != null) {
			javaOptions.add(envOptions);
		}

		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return quotedStringToList(javaOptions.stream().collect(Collectors.joining(" ")));
	}

	public List<String> collectRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	public List<String> collectCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath() {
		if (classpath == null) {
			List<String> dependencies = collectDependencies();

			classpath = new ModularClassPath(
					new DependencyUtil().resolveDependencies(dependencies, Collections.emptyList(), true));
		}
		if (jar != null) {
			return classpath.getClassPath() + Settings.CP_SEPARATOR + jar.getAbsolutePath();
		}
		return classpath.getClassPath();
	}

	List<String> getAutoDetectedModuleArguments(String javacmd) {
		if (classpath == null) {
			resolveClassPath();
		}
		return classpath.getAutoDectectedModuleArguments(javacmd);
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

	public boolean needsJar() {
		// anything but .jsh files needs jar
		return !forJShell();
	}

	public boolean forJShell() {
		return backingFile.getName().endsWith(".jsh");
	}

	public void setOriginal(File probe) {
		this.originalFile = probe;
	}

	public File getOriginalFile() {
		return originalFile;
	}
}
