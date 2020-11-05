package dev.jbang;

import static dev.jbang.FileRef.isURL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
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
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Script {

	public static final String BUILD_JDK = "Build-Jdk";
	public static final String JBANG_JAVA_OPTIONS = "JBang-Java-Options";
	private static final String DEPS_COMMENT_PREFIX = "//DEPS ";
	private static final String FILES_COMMENT_PREFIX = "//FILES ";
	private static final String SOURCES_COMMENT_PREFIX = "//SOURCES ";

	private static final String DEPS_ANNOT_PREFIX = "@Grab(";
	private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

	private static final String REPOS_COMMENT_PREFIX = "//REPOS ";
	private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
	private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile("@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");
	private final ScriptResource scriptResource;

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

	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();

	private Map<String, String> properties;
	private List<MavenRepo> repositories;
	private List<FileRef> filerefs;
	private List<String> persistentJvmArgs;
	private List<FileRef> sources;
	private List<Source> resolvedSources;
	private List<Script> javaAgents;
	private List<KeyValue> agentOptions;
	private String preMainClass;
	private String agentMainClass;
	/**
	 * if this script is used as an agent, agentOption is the option needed to pass
	 * in
	 **/
	private String javaAgentOption;

	public Script(ScriptResource resource, String content, List<String> arguments, Map<String, String> properties)
			throws FileNotFoundException {
		this.scriptResource = resource;
		this.script = content;
		this.arguments = arguments;
		this.properties = properties;
	}

	public Script(ScriptResource resource, List<String> arguments, Map<String, String> properties)
			throws FileNotFoundException {
		this.scriptResource = resource;
		this.arguments = arguments;
		this.properties = properties;
		if (!forJar()) {
			try (Scanner sc = new Scanner(this.getBackingFile())) {
				sc.useDelimiter("\\Z");
				this.script = sc.next();
			}
		}
	}

	public Script(String script, List<String> arguments, Map<String, String> properties) {
		this.scriptResource = new ScriptResource(null, null, null);
		this.script = script;
		this.arguments = arguments;
		this.properties = properties;
	}

	public ScriptResource getScriptResource() {
		return scriptResource;
	}

	List<String> getLines() {
		if (lines == null && script != null) {
			lines = Arrays.asList(script.split("\\r?\\n"));
		}
		return lines;
	}

	public List<String> collectDependencies() {
		return collectDependencies(getLines());
	}

	public List<String> collectDependencies(List<String> lines) {
		if (forJar()) { // if a .jar then we don't try parse it for dependencies.
			return additionalDeps;
		}
		// early/eager init to property resolution will work.
		new Detector().detect(new Properties(), Collections.emptyList());

		// Make sure that dependencies declarations are well formatted
		if (lines.stream().anyMatch(it -> it.startsWith("// DEPS"))) {
			throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
		}

		Properties p = new Properties(System.getProperties());
		if (properties != null) {
			p.putAll(properties);
		}

		Stream<String> depStream = lines.stream()
										.filter(it -> isDependDeclare(it))
										.flatMap(it -> extractDependencies(it))
										.map(it -> PropertiesValueResolver.replaceProperties(it, p));

		List<String> dependencies = Stream	.concat(additionalDeps.stream(), depStream)
											.collect(Collectors.toList());

		return dependencies;
	}

	private List<KeyValue> collectAgentOptions() {
		if (agentOptions == null) {
			agentOptions = collectRawOptions("JAVAAGENT")	.stream()
															.map(PropertiesValueResolver::replaceProperties)
															.flatMap(Script::extractKeyValue)
															.map(Script::toKeyValue)
															.collect(Collectors.toCollection(ArrayList::new));
		}
		return agentOptions;
	}

	public List<MavenRepo> getRepositories() {
		if (repositories == null) {
			repositories = getLines()	.stream()
										.filter(Script::isRepoDeclare)
										.flatMap(Script::extractRepositories)
										.map(PropertiesValueResolver::replaceProperties)
										.map(DependencyUtil::toMavenRepo)
										.collect(Collectors.toCollection(ArrayList::new));
		}
		return repositories;
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
										.filter(it -> it.startsWith(joptsPrefix + " ")
												|| it.startsWith(joptsPrefix + "\t") || it.equals(joptsPrefix))
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

	public String javaVersion() {
		List<String> opts = collectRawOptions("JAVA");
		if (!opts.isEmpty()) {
			// If there are multiple //JAVA_VERSIONs we'll use the last one
			String version = opts.get(opts.size() - 1);
			if (!version.matches("\\d+[+]?")) {
				throw new IllegalArgumentException(
						"Invalid JAVA version, should be a number optionally followed by a plus sign");
			}
			return version;
		} else {
			return null;
		}
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			if (forJar()) {
				if (DependencyUtil.looksLikeAGav(scriptResource.getOriginalResource())) {
					List<String> dependencies = new ArrayList<>(additionalDeps);
					dependencies.add(scriptResource.getOriginalResource());
					classpath = new DependencyUtil().resolveDependencies(dependencies,
							Collections.emptyList(), offline, !Util.isQuiet());
				} else if (!additionalDeps.isEmpty()) {
					classpath = new DependencyUtil().resolveDependencies(additionalDeps,
							Collections.emptyList(), offline, !Util.isQuiet());
				} else {
					classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, new File(originalFile))));
				}
				// fetch main class as we can't use -jar to run as it ignores classpath.
				if (getMainClass() == null) {
					try (JarFile jf = new JarFile(getBackingFile())) {
						setMainClass(
								jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
					} catch (IOException e) {
						Util.warnMsg("Problem reading manifest from " + getBackingFile());
					}
				}
			} else {
				List<String> dependencies = collectDependencies();
				if (getResolvedSources() != null) {
					for (Source source : getResolvedSources()) {
						try {
							dependencies.addAll(collectDependencies(Files.readAllLines(source.getResolvedPath())));
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
				List<MavenRepo> repositories = getRepositories();
				classpath = new DependencyUtil().resolveDependencies(dependencies, repositories, offline,
						!Util.isQuiet());
			}
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : additionalClasspaths) {
			cp.append(Settings.CP_SEPARATOR + addcp);
		}
		if (jar != null) {
			return jar.getAbsolutePath() + Settings.CP_SEPARATOR + cp.toString();
		}
		return cp.toString();
	}

	public List<String> getAutoDetectedModuleArguments(String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public List<String> getPersistentJvmArgs() {
		return persistentJvmArgs;
	}

	public Script setPersistentJvmArgs(List<String> persistentJvmArgs) {
		this.persistentJvmArgs = persistentJvmArgs;
		return this;
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

	public Script setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
		return this;
	}

	public Script setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
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
		return getBackingFile().getName().endsWith(".jsh");
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

		if (isAgent()) {
			if (getPreMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name("Premain-Class"), getPreMainClass());
			}
			if (getAgentMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name("Agent-Class"), getAgentMainClass());
			}

			for (KeyValue kv : getAgentOptions()) {
				if (kv.getKey().trim().isEmpty()) {
					continue;
				}
				Attributes.Name k = new Attributes.Name(kv.getKey());
				String v = kv.getValue() == null ? "true" : kv.getValue();
				manifest.getMainAttributes().put(k, v);
			}

			String bootClasspath = getClassPath().getClassPath().replace(Settings.CP_SEPARATOR, " ");
			if (!bootClasspath.isEmpty()) {
				manifest.getMainAttributes().put(new Attributes.Name("Boot-Class-Path"), getClassPath().getClassPath());
			}
		}

		if (persistentJvmArgs != null) {
			manifest.getMainAttributes().putValue("JBang-Java-Options", String.join(" ", persistentJvmArgs));
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
		return getBackingFile() != null && getBackingFile().toString().endsWith(".jar");
	}

	public File getBackingFile() {
		return scriptResource.getFile();
	}

	public ModularClassPath getClassPath() {
		return classpath;
	}

	static public FileRef toFileRef(Script source, String fileReference) {
		String[] split = fileReference.split("=");
		String ref = null;
		String dest = null;

		if (split.length == 1) {
			ref = split[0];
		} else if (split.length == 2) {
			ref = split[0];
			dest = split[1];
		} else {
			throw new IllegalStateException("Invalid file reference: " + fileReference);
		}

		if (isURL(fileReference)) {
			return new URLRef(source, ref, dest);
		}
		if (isURL(source.originalFile)) {
			return new URLRef(source, ref, dest);
		} else {
			return new FileRef(source, ref, dest);
		}
	}

	static Stream<String> extractKeyValue(String line) {
		return Arrays.stream(line.split(" +")).map(String::trim);
	}

	static public KeyValue toKeyValue(String line) {

		String[] split = line.split("=");
		String key = null;
		String value = null;

		if (split.length == 1) {
			key = split[0];
		} else if (split.length == 2) {
			key = split[0];
			value = split[1];
		} else {
			throw new IllegalStateException("Invalid key/value: " + line);
		}
		return new KeyValue(key, value);
	}

	public List<FileRef> collectFiles() {

		if (filerefs == null) {
			filerefs = getLines()	.stream()
									.filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
									.flatMap(line -> Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim))
									.map(PropertiesValueResolver::replaceProperties)
									.map(line -> toFileRef(this, line))
									.collect(Collectors.toCollection(ArrayList::new));
		}
		return filerefs;
	}

	public List<FileRef> collectSources() {

		if (sources == null) {
			if (getLines() == null) {
				sources = Collections.emptyList();
			} else {
				sources = getLines().stream()
									.filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
									.flatMap(line -> Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim))
									.map(PropertiesValueResolver::replaceProperties)
									.flatMap(line -> Util
															.explode(getScriptResource().getOriginalResource(),
																	getBackingFile().getAbsoluteFile()
																					.getParentFile()
																					.toPath(),
																	line)
															.stream())
									.map(line -> toFileRef(this, line.toString()))
									.collect(Collectors.toCollection(ArrayList::new));
			}
		}
		return sources;
	}

	public void setResolvedSources(List<Source> resolvedSourcePaths) {
		this.resolvedSources = resolvedSourcePaths;
	}

	public List<Source> getResolvedSources() {
		return resolvedSources;
	}

	public List<Script> getJavaAgents() {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		return javaAgents;
	}

	public boolean isAgent() {
		if (agentOptions == null) {
			agentOptions = collectAgentOptions();
		}
		return !agentOptions.isEmpty();
	}

	public void setAgentMainClass(String b) {
		agentMainClass = b;
	}

	public String getAgentMainClass() {
		return agentMainClass;
	}

	public void setPreMainClass(String name) {
		preMainClass = name;
	}

	public String getPreMainClass() {
		return preMainClass;
	}

	public void setJavaAgentOption(String option) {
		this.javaAgentOption = option;
	}

	public void addJavaAgent(Script agentScript) {
		getJavaAgents().add(agentScript);
	}

	public String getJavaAgentOption() {
		return javaAgentOption;
	}

	public List<KeyValue> getAgentOptions() {
		return agentOptions;
	}

}
