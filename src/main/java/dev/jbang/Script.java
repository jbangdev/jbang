package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.cli.BaseCommand;

public class Script implements RunUnit {

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

	private final ResourceRef resourceRef;
	private final String script;

	// Cached values
	private List<String> lines;
	private List<MavenRepo> repositories;
	private List<FileRef> filerefs;
	private List<Script> sources;
	private List<KeyValue> agentOptions;
	private File jar;

	protected Script(String script) {
		this(ResourceRef.forFile(null), script);
	}

	private Script(ResourceRef resourceRef) {
		this(resourceRef, getBackingFileContent(resourceRef.getFile()));
	}

	private Script(ResourceRef resourceRef, String content) {
		this.resourceRef = resourceRef;
		this.script = content;
	}

	@Override
	public ResourceRef getResourceRef() {
		return resourceRef;
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

	@Override
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
					.filter(Script::isDependDeclare)
					.flatMap(Script::extractDependencies)
					.map(it -> PropertiesValueResolver.replaceProperties(it, props))
					.collect(Collectors.toList());
	}

	static boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX) || line.contains(DEPS_ANNOT_PREFIX);
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
				String gav = Stream.of(
						args.get("group"),
						args.get("module"),
						args.get("version"),
						args.get("classifier")).filter(Objects::nonNull).collect(Collectors.joining(":"));
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

	public ModularClassPath resolveClassPath(List<String> dependencies, boolean offline) {
		ModularClassPath classpath;
		List<MavenRepo> repositories = collectAllRepositories();
		classpath = new DependencyUtil().resolveDependencies(dependencies, repositories, offline,
				!Util.isQuiet());
		return classpath;
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

	static Stream<String> extractKeyValue(String line) {
		return Arrays.stream(line.split(" +")).map(String::trim);
	}

	static public KeyValue toKeyValue(String line) {

		String[] split = line.split("=");
		String key;
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

	public boolean isAgent() {
		if (agentOptions == null) {
			agentOptions = collectAllAgentOptions();
		}
		return !agentOptions.isEmpty();
	}

	public List<KeyValue> getAgentOptions() {
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

	static boolean isRepoDeclare(String line) {
		return line.startsWith(REPOS_COMMENT_PREFIX) || line.contains(REPOS_ANNOT_PREFIX);
	}

	static Stream<String> extractRepositories(String line) {
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

	public String getDescription() {
		return getLines()	.stream()
							.filter(Script::isDescriptionDeclare)
							.map(s -> s.substring(DESCRIPTION_COMMENT_PREFIX.length()))
							.collect(Collectors.joining("\n"));
	}

	static boolean isDescriptionDeclare(String line) {
		return line.startsWith(DESCRIPTION_COMMENT_PREFIX);
	}

	private List<String> collectOptions(String prefix) {
		List<String> javaOptions = collectRawOptions(prefix);

		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return quotedStringToList(String.join(" ", javaOptions));
	}

	// https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
	List<String> quotedStringToList(String subjectString) {
		List<String> matchList = new ArrayList<>();
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

	@Override
	public String javaVersion() {
		Optional<String> version = collectAll(Script::collectJavaVersions)	.stream()
																			.filter(JavaUtil::checkRequestedVersion)
																			.max(new JavaUtil.RequestedVersionComparator());
		return version.orElse(null);
	}

	private List<String> collectJavaVersions() {
		return collectOptions("JAVA");
	}

	public File getJar() {
		if (jar == null) {
			File baseDir = Settings.getCacheDir(Settings.CacheClass.jars).toFile();
			File tmpJarDir = new File(baseDir, getBackingFile().getName() +
					"." + Util.getStableID(getBackingFile()));
			jar = new File(tmpJarDir.getParentFile(), tmpJarDir.getName() + ".jar");
		}
		return jar;
	}

	@Override
	public File getBackingFile() {
		return resourceRef.getFile();
	}

	static private String getBackingFileContent(File backingFile) {
		try (Scanner sc = new Scanner(backingFile)) {
			sc.useDelimiter("\\Z");
			return sc.hasNext() ? sc.next() : "";
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE,
					"Could not read script content for " + backingFile);
		}
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
									.map(this::toFileRef)
									.collect(Collectors.toCollection(ArrayList::new));
		}
		return filerefs;
	}

	private FileRef toFileRef(String fileReference) {
		String[] split = fileReference.split(" // ")[0].split("=");
		String ref;
		String dest = null;

		if (split.length == 1) {
			ref = split[0];
		} else if (split.length == 2) {
			ref = split[0];
			dest = split[1];
		} else {
			throw new IllegalStateException("Invalid file reference: " + fileReference);
		}

		String origResource = resourceRef.getOriginalResource();
		if (FileRef.isURL(fileReference) || FileRef.isURL(origResource)) {
			return new URLRef(origResource, ref, dest);
		} else {
			return new FileRef(origResource, ref, dest);
		}
	}

	public List<Script> collectAllSources() {
		List<Script> scripts = new ArrayList<>();
		HashSet<ResourceRef> refs = new HashSet<>();
		// We should only return sources but we must avoid circular references via this
		// script, so we add this script's ref but not the script itself
		refs.add(resourceRef);
		collectAllSources(refs, scripts);
		return scripts;
	}

	private void collectAllSources(Set<ResourceRef> refs, List<Script> scripts) {
		List<Script> srcs = collectSources();
		for (Script s : srcs) {
			if (!refs.contains(s.resourceRef)) {
				refs.add(s.resourceRef);
				scripts.add(s);
				s.collectAllSources(refs, scripts);
			}
		}
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
															.explode(getResourceRef().getOriginalResource(),
																	getBackingFile().getAbsoluteFile()
																					.getParentFile()
																					.toPath(),
																	line)
															.stream())
									.map(resourceRef::asSibling)
									.map(Script::prepareScript)
									.collect(Collectors.toCollection(ArrayList::new));
			}
		}
		return sources;
	}

	private <R> List<R> collectAll(Function<Script, List<R>> func) {
		Stream<R> subs = collectAllSources().stream().map(s -> func.apply(s).stream()).flatMap(i -> i);
		return Stream.concat(func.apply(this).stream(), subs).collect(Collectors.toList());
	}

	public static Script prepareScript(String resource) {
		ResourceRef resourceRef = ResourceRef.forResource(resource);
		return prepareScript(resourceRef);
	}

	public static Script prepareScript(ResourceRef resourceRef) {
		return new Script(resourceRef);
	}
}
