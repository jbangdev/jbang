package dev.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Script {

	private static final String DEPS_COMMENT_PREFIX = "//DEPS ";

	private static final String DEPS_ANNOT_PREFIX = "@Grab(";
	private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

	private static final String REPOS_COMMENT_PREFIX = "//REPOS ";
	private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
	private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile("@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");

	/**
	 * the file that contains the code that will back the actual compile/execution.
	 * Might have been altered to be runnable; i.e. stripped out !# before launch.
	 */
	private File backingFile;

	/**
	 * The original reference, it might or might not be same as used as backingFile.
	 * TODO: should probably have a "originalRef" to capture GAV+main ref and a
	 * "originalFile" which could be null.
	 */
	private String originalFile;

	private ModularClassPath classpath;
	private String script;
	private String mainClass;
	private int buildJdk;
	private File jar;
	List<String> lines;
	// true if in this run a jar was build/created
	private boolean createdJar;

	private List<String> arguments;

	private Map<String, String> properties;

	public Script(File backingFile, String content, List<String> arguments, Map<String, String> properties)
			throws FileNotFoundException {
		this.backingFile = backingFile;
		this.script = content;
		this.arguments = arguments;
		this.properties = properties;
	}

	public Script(File backingFile, List<String> arguments, Map<String, String> properties)
			throws FileNotFoundException {
		this.backingFile = backingFile;
		this.arguments = arguments;
		this.properties = properties;
		if (!forJar()) {
			try (Scanner sc = new Scanner(this.backingFile)) {
				sc.useDelimiter("\\Z");
				this.script = sc.next();
			}
		}
	}

	public Script(String script, List<String> arguments, Map<String, String> properties) {
		this.backingFile = null;
		this.script = script;
		this.arguments = arguments;
		this.properties = properties;
	}

	private List<String> getLines() {
		if (lines == null) {
			lines = Arrays.asList(script.split("\\r?\\n"));
		}
		return lines;
	}

	public List<String> collectDependencies() {
		if (forJar()) { // if a .jar then we don't try parse it for dependencies.
			return Collections.emptyList();
		}
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

	public List<MavenRepo> collectRepositories() {
		return getLines()	.stream()
							.filter(Script::isRepoDeclare)
							.flatMap(Script::extractRepositories)
							.map(PropertiesValueResolver::replaceProperties)
							.map(DependencyUtil::toMavenRepo)
							.collect(Collectors.toCollection(ArrayList::new));
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
		List<String> javaOptions = collectRawOptions(prefix);

		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return quotedStringToList(javaOptions.stream().collect(Collectors.joining(" ")));
	}

	private List<String> collectRawOptions(String prefix) {
		if (forJar())
			return Collections.emptyList();

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
		return javaOptions;
	}

	public List<String> collectRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	public List<String> collectCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	public boolean enableCDS() {
		return !collectRawOptions("CDS").isEmpty();
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			if (forJar()) {
				if (DependencyUtil.looksLikeAGav(originalFile.toString())) {
					String gav = originalFile.toString();
					classpath = new ModularClassPath(
							new DependencyUtil().resolveDependencies(Arrays.asList(gav.toString()),
									Collections.emptyList(), offline, true));
				} else {
					classpath = new ModularClassPath("");
				}
			} else {
				List<String> dependencies = collectDependencies();
				List<MavenRepo> repositories = collectRepositories();
				classpath = new ModularClassPath(
						new DependencyUtil().resolveDependencies(dependencies, repositories, offline, true));
			}
		}
		if (jar != null) {
			return classpath.getClassPath() + Settings.CP_SEPARATOR + jar.getAbsolutePath();
		}
		return classpath.getClassPath();
	}

	public List<String> getAutoDetectedModuleArguments(String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public static Stream<String> extractRepositories(String line) {
		if (line.startsWith(REPOS_COMMENT_PREFIX)) {
			return Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim);
		}

		if (line.contains(REPOS_ANNOT_PREFIX)) {
			Map<String, String> args = new HashMap<>();

			Matcher matcher = REPOS_ANNOT_PAIRS.matcher(line);
			while (matcher.find()) {
				args.put(matcher.group("key"), matcher.group("value"));
			}
			if (!args.isEmpty()) {
				String repo = args.getOrDefault("name", args.get("root")) + "=" + args.get("root");
				return Stream.of(repo);
			} else {
				matcher = REPOS_ANNOT_SINGLE.matcher(line);
				if (matcher.find()) {
					return Stream.of(matcher.group("value"));
				}
			}
		}

		return Stream.of();
	}

	static Stream<String> extractDependencies(String line) {
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

	static boolean isRepoDeclare(String line) {
		return line.startsWith(REPOS_COMMENT_PREFIX) || line.contains(REPOS_ANNOT_PREFIX);
	}

	static boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX) || line.contains(DEPS_ANNOT_PREFIX);
	}

	public Script setMainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public Script setBuildJdk(int javaVersion) {
		this.buildJdk = javaVersion;
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

	public int getBuildJdk() {
		return buildJdk;
	}

	public boolean needsJar() {
		// anything but .jar and .jsh files needs jar
		return !(forJar() || forJShell());
	}

	public boolean forJShell() {
		return backingFile.getName().endsWith(".jsh");
	}

	public void setOriginal(String probe) {
		this.originalFile = probe;
	}

	public String getOriginalFile() {
		return originalFile;
	}

	public void createJarFile(File path, File output) throws IOException {
		this.createdJar = true;

		String mainclass = getMainClass();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue("Build-Jdk", val);
		}

		FileOutputStream target = new FileOutputStream(output);
		JarUtil.jar(target, path.listFiles(), null, null, manifest);
		target.close();
	}

	public boolean wasJarCreated() {
		return createdJar;
	}

	public List<String> getArguments() {
		return (arguments != null) ? arguments : Collections.emptyList();
	}

	public Map<String, String> getProperties() {
		return (properties != null) ? properties : Collections.emptyMap();
	}

	public boolean forJar() {
		return backingFile != null && backingFile.toString().endsWith(".jar");
	}

	public File getBackingFile() {
		return backingFile;
	}

}
