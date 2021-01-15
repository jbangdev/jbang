package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.cli.BaseCommand;

public class Script {

	public static final String BUILD_JDK = "Build-Jdk";
	public static final String JBANG_JAVA_OPTIONS = "JBang-Java-Options";
	private static final String DEPS_COMMENT_PREFIX = "//DEPS ";
	private static final String FILES_COMMENT_PREFIX = "//FILES ";
	private static final String SOURCES_COMMENT_PREFIX = "//SOURCES ";
	private static final String DESCRIPTION_COMMENT_PREFIX = "//DESCRIPTION ";

	private static final String DEPS_ANNOT_PREFIX = "@Grab(";
	private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

	private static final String REPOS_COMMENT_PREFIX = "//REPOS ";
	private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
	private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile("@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");

	protected final ScriptResource scriptResource;

	private String originalRef;
	private AliasUtil.Alias alias;

	private String script;
	private String mainClass;
	private int buildJdk;
	private File jar;
	List<String> lines;

	private List<MavenRepo> repositories;
	private List<FileRef> filerefs;
	private List<String> persistentJvmArgs;
	private List<Script> sources;
	private List<Script> javaAgents;
	private List<KeyValue> agentOptions;
	private String preMainClass;
	private String agentMainClass;
	/**
	 * if this script is used as an agent, agentOption is the option needed to pass
	 * in
	 **/
	private String javaAgentOption;

	protected Script(ScriptResource resource) {
		this(resource, getBackingFileContent(resource.getFile()));
	}

	protected Script(String script) {
		this(ScriptResource.forFile(null), script);
	}

	protected Script(ScriptResource resource, String content) {
		this.scriptResource = resource;
		this.script = content;
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

	public Optional<String> getJavaPackage() {
		if (script != null) {
			return Util.getSourcePackage(script);
		} else {
			return Optional.empty();
		}
	}

	public List<String> collectAllDependencies(Properties props) {
		return collectAll(script -> script.collectDependencies(props));
	}

	private List<String> collectDependencies(Properties props) {
		return collectDependencies(getLines(), props);
	}

	private List<String> collectDependencies(List<String> lines, Properties props) {
		// early/eager init to property resolution will work.
		new Detector().detect(new Properties(), Collections.emptyList());

		// Make sure that dependencies declarations are well formatted
		if (lines.stream().anyMatch(it -> it.startsWith("// DEPS"))) {
			throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
		}

		return lines.stream()
					.filter(it -> isDependDeclare(it))
					.flatMap(it -> extractDependencies(it))
					.map(it -> PropertiesValueResolver.replaceProperties(it, props))
					.collect(Collectors.toList());
	}

	public List<KeyValue> collectAllAgentOptions() {
		return collectAll(Script::collectAgentOptions);
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

	public List<MavenRepo> collectAllRepositories() {
		return collectAll(Script::collectRepositories);
	}

	private List<MavenRepo> collectRepositories() {
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

	public String getDescription() {
		return getLines()	.stream()
							.filter(Script::isDescriptionDeclare)
							.map(s -> s.substring(DESCRIPTION_COMMENT_PREFIX.length()))
							.collect(Collectors.joining("\n"));
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
		// if (forJar())
		// return Collections.emptyList();

		String joptsPrefix = "//" + prefix;

		List<String> lines = getLines();

		List<String> javaOptions = lines.stream()
										.map(it -> it.split(" // ")[0]) // strip away nested comments.
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

	public List<String> collectAllRuntimeOptions() {
		return collectAll(Script::collectRuntimeOptions);
	}

	private List<String> collectRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	public List<String> collectAllCompileOptions() {
		return collectAll(Script::collectCompileOptions);
	}

	private List<String> collectCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	public boolean enableCDS() {
		return !collectRawOptions("CDS").isEmpty();
	}

	public String javaVersion() {
		Optional<String> version = collectAll(Script::collectJavaVersions)	.stream()
																			.filter(JavaUtil::checkRequestedVersion)
																			.max(new JavaUtil.RequestedVersionComparator());
		if (version.isPresent()) {
			return version.get();
		} else {
			return null;
		}
	}

	private List<String> collectJavaVersions() {
		return collectOptions("JAVA");
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
			return Arrays.stream(line.split(" // ")[0].split("[ ;,]+")).skip(1).map(String::trim);
		}

		if (line.contains(REPOS_ANNOT_PREFIX)) {
			if (line.indexOf(REPOS_ANNOT_PREFIX) > line.indexOf("//")) {
				// ignore if on line that is a comment
				return Stream.of();
			}

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
			return Arrays.stream(line.split(" // ")[0].split("[ ;,]+")).skip(1).map(String::trim);
		}

		if (line.contains(DEPS_ANNOT_PREFIX)) {
			int commentOrEnd = line.indexOf("//");
			if (commentOrEnd < 0) {
				commentOrEnd = line.length();
			}
			if (line.indexOf(DEPS_ANNOT_PREFIX) > commentOrEnd) {
				// ignore if on line that is a comment
				return Stream.of();
			}

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

	static boolean isDescriptionDeclare(String line) {
		return line.startsWith(DESCRIPTION_COMMENT_PREFIX);
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

	public boolean forJShell() {
		return getBackingFile().getName().endsWith(".jsh");
	}

	public void setOriginal(String ref) {
		this.originalRef = ref;
	}

	/**
	 * The original script reference. Might ba a URL or an alias.
	 */
	public String getOriginalRef() {
		return originalRef;
	}

	/**
	 * The resource that `originalRef` resolves to. Wil be a URL or file path.
	 */
	public String getOriginalResource() {
		return scriptResource.getOriginalResource();
	}

	/**
	 * The resource that `originalRef` resolves to. Will be a URL or file path.
	 */
	public File getOriginalFile() {
		return new File(getOriginalResource());
	}

	/**
	 * Sets the Alias object if originalRef is an alias
	 */
	public void setAlias(AliasUtil.Alias alias) {
		this.alias = alias;
	}

	/**
	 * Returns the Alias object if originalRef is an alias, otherwise null
	 */
	public AliasUtil.Alias getAlias() {
		return alias;
	}

	/**
	 * The actual local file that `originalRef` refers to. This might be the sane as
	 * `originalRef` if that pointed to a file on the local file system, in all
	 * other cases it will refer to a downloaded copy in Jbang's cache.
	 */
	public File getBackingFile() {
		return scriptResource.getFile();
	}

	public boolean forJar() {
		return Script.forJar(getBackingFile());
	}

	protected static boolean forJar(File backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jar");
	}

	static private String getBackingFileContent(File backingFile) {
		if (!forJar(backingFile)) {
			try (Scanner sc = new Scanner(backingFile)) {
				sc.useDelimiter("\\Z");
				return sc.hasNext() ? sc.next() : "";
			} catch (IOException e) {
				throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE,
						"Could not read script content for " + backingFile);
			}
		}
		return "";
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

	public List<FileRef> collectAllFiles() {
		return collectAll(Script::collectFiles);
	}

	private List<FileRef> collectFiles() {
		if (filerefs == null) {
			filerefs = getLines()	.stream()
									.filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
									.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
															.skip(1)
															.map(String::trim))
									.map(PropertiesValueResolver::replaceProperties)
									.map(line -> toFileRef(line))
									.collect(Collectors.toCollection(ArrayList::new));
		}
		return filerefs;
	}

	private FileRef toFileRef(String fileReference) {
		String[] split = fileReference.split(" // ")[0].split("=");
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

		String origResource = scriptResource.getOriginalResource();
		if (FileRef.isURL(fileReference) || FileRef.isURL(origResource)) {
			return new URLRef(origResource, ref, dest);
		} else {
			return new FileRef(origResource, ref, dest);
		}
	}

	public List<Script> collectAllSources() {
		Stream<Script> ss = collectSources().stream().map(s -> s.collectAllSources().stream()).flatMap(i -> i);
		return Stream.concat(collectSources().stream(), ss).collect(Collectors.toList());
	}

	private List<Script> collectSources() {
		if (sources == null) {
			if (getLines() == null) {
				sources = Collections.emptyList();
			} else {
				sources = getLines().stream()
									.filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
									.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
															.skip(1)
															.map(String::trim))
									.map(PropertiesValueResolver::replaceProperties)
									.flatMap(line -> Util
															.explode(getScriptResource().getOriginalResource(),
																	getBackingFile().getAbsoluteFile()
																					.getParentFile()
																					.toPath(),
																	line)
															.stream())
									.map(resource -> toScript(resource))
									.collect(Collectors.toCollection(ArrayList::new));
			}
		}
		return sources;
	}

	private Script toScript(String resource) {
		try {
			ScriptResource sibling = scriptResource.asSibling(resource);
			Script s = new Script(sibling);
			s.setOriginal(resource);
			return s;
		} catch (URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, e);
		}
	}

	private <R> List<R> collectAll(Function<Script, List<R>> func) {
		Stream<R> subs = collectAllSources().stream().map(s -> func.apply(s).stream()).flatMap(i -> i);
		return Stream.concat(func.apply(this).stream(), subs).collect(Collectors.toList());
	}

	public List<Script> getJavaAgents() {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		return javaAgents;
	}

	public boolean isAgent() {
		if (agentOptions == null) {
			agentOptions = collectAllAgentOptions();
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

	public static Script prepareScript(String resource) {
		ScriptResource scriptFile = ScriptResource.forResource(resource);
		Script s = new Script(scriptFile);
		return s;
	}
}
