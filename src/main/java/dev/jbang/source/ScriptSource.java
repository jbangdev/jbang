package dev.jbang.source;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.Detector;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

/**
 * A Script represents a Source (something runnable) in the form of a source
 * file. It's code that first needs to be compiled before it can be executed.
 * The Script extracts as much information from the source file as it can, like
 * all `//`-directives (eg. `//SOURCES`, `//DEPS`, etc.)
 *
 * NB: The Script contains/returns no other information than that which can be
 * induced from the source file. So all Scripts that refer to the same source
 * file will contain/return the exact same information.
 */
public class ScriptSource implements Source {

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
	private List<String> dependencies;
	private List<FileRef> filerefs;
	private List<ScriptSource> sources;
	private List<KeyValue> agentOptions;
	private Optional<String> description = Optional.empty();
	private File jar;

	protected ScriptSource(String script) {
		this(ResourceRef.forFile(null), script);
	}

	private ScriptSource(ResourceRef resourceRef) {
		this(resourceRef, getBackingFileContent(resourceRef.getFile()));
	}

	private ScriptSource(ResourceRef resourceRef, String content) {
		this.resourceRef = resourceRef;
		this.script = content;
	}

	@Override
	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	public List<String> getLines() {
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
	public List<String> getAllDependencies(Properties props) {
		if (dependencies == null) {
			dependencies = collectAll(script -> script.collectDependencies(props));
		}
		return dependencies;
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
					.filter(ScriptSource::isDependDeclare)
					.flatMap(ScriptSource::extractDependencies)
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

	@Override
	public ModularClassPath resolveClassPath(List<String> dependencies) {
		ModularClassPath classpath;
		List<MavenRepo> repositories = getAllRepositories();
		classpath = new DependencyUtil().resolveDependencies(dependencies, repositories, Util.isOffline(),
				!Util.isQuiet());
		return classpath;
	}

	public static class KeyValue {
		final String key;
		final String value;

		public KeyValue(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public String toManifestString() {
			return getKey() + ": " + value;
		}

		@Override
		public String toString() {
			return getKey() + "=" + getValue() == null ? "" : getValue();
		}
	}

	public List<KeyValue> getAllAgentOptions() {
		if (agentOptions == null) {
			agentOptions = collectAll(ScriptSource::collectAgentOptions);
		}
		return agentOptions;
	}

	private List<KeyValue> collectAgentOptions() {
		return collectRawOptions("JAVAAGENT")	.stream()
												.map(PropertiesValueResolver::replaceProperties)
												.flatMap(ScriptSource::extractKeyValue)
												.map(ScriptSource::toKeyValue)
												.collect(Collectors.toCollection(ArrayList::new));
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
		return !getAllAgentOptions().isEmpty();
	}

	public List<MavenRepo> getAllRepositories() {
		if (repositories == null) {
			repositories = collectAll(ScriptSource::collectRepositories);
		}
		return repositories;
	}

	private List<MavenRepo> collectRepositories() {
		return getLines()	.stream()
							.filter(ScriptSource::isRepoDeclare)
							.flatMap(ScriptSource::extractRepositories)
							.map(PropertiesValueResolver::replaceProperties)
							.map(DependencyUtil::toMavenRepo)
							.collect(Collectors.toCollection(ArrayList::new));
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

	@Override
	public Optional<String> getDescription() {
		if (!description.isPresent()) {
			String desc = getLines().stream()
									.filter(ScriptSource::isDescriptionDeclare)
									.map(s -> s.substring(DESCRIPTION_COMMENT_PREFIX.length()))
									.collect(Collectors.joining("\n"));
			if (desc.isEmpty()) {
				description = Optional.empty();
			} else {
				description = Optional.of(desc);
			}
		}
		return description;
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

	@Override
	public List<String> getRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	public List<String> getCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	@Override
	public boolean enableCDS() {
		return !collectRawOptions("CDS").isEmpty();
	}

	@Override
	public String javaVersion() {
		Optional<String> version = collectAll(ScriptSource::collectJavaVersions).stream()
																				.filter(JavaUtil::checkRequestedVersion)
																				.max(new JavaUtil.RequestedVersionComparator());
		return version.orElse(null);
	}

	private List<String> collectJavaVersions() {
		return collectOptions("JAVA");
	}

	@Override
	public boolean isJShell() {
		return Source.isJShell(getResourceRef().getFile());
	}

	@Override
	public File getJarFile() {
		if (isJShell()) {
			return null;
		}
		if (jar == null) {
			File baseDir = Settings.getCacheDir(Cache.CacheClass.jars).toFile();
			File tmpJarDir = new File(baseDir, getResourceRef().getFile().getName() +
					"." + Util.getStableID(script));
			jar = new File(tmpJarDir.getParentFile(), tmpJarDir.getName() + ".jar");
		}
		return jar;
	}

	@Override
	public JarSource asJarSource() {
		JarSource result = null;
		File jarFile = getJarFile();
		if (jarFile != null && jarFile.exists()) {
			JarSource jarSrc = JarSource.prepareJar(
					ResourceRef.forNamedFile(getResourceRef().getOriginalResource(), jarFile));
			if (jarSrc.resolveClassPath(Collections.emptyList()).isValid()) {
				result = jarSrc;
			}
		}
		return result;
	}

	static private String getBackingFileContent(File backingFile) {
		try {
			return new String(Files.readAllBytes(backingFile.toPath()));
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE,
					"Could not read script content for " + backingFile, e);
		}
	}

	public void copyFilesTo(Path dest) {
		List<FileRef> files = getAllFiles();
		for (FileRef file : files) {
			file.copy(dest);
		}
	}

	private List<FileRef> getAllFiles() {
		if (filerefs == null) {
			filerefs = collectAll(ScriptSource::collectFiles);
		}
		return filerefs;
	}

	private List<FileRef> collectFiles() {
		return getLines()	.stream()
							.filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
							.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
													.skip(1)
													.map(String::trim))
							.map(PropertiesValueResolver::replaceProperties)
							.map(this::toFileRef)
							.collect(Collectors.toCollection(ArrayList::new));
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

	public List<ScriptSource> getAllSources() {
		if (sources == null) {
			List<ScriptSource> scripts = new ArrayList<>();
			HashSet<ResourceRef> refs = new HashSet<>();
			// We should only return sources but we must avoid circular references via this
			// script, so we add this script's ref but not the script itself
			refs.add(resourceRef);
			collectAllSources(refs, scripts);
			sources = scripts;
		}
		return sources;
	}

	private void collectAllSources(Set<ResourceRef> refs, List<ScriptSource> scripts) {
		List<ScriptSource> srcs = collectSources();
		for (ScriptSource s : srcs) {
			if (!refs.contains(s.resourceRef)) {
				refs.add(s.resourceRef);
				scripts.add(s);
				s.collectAllSources(refs, scripts);
			}
		}
	}

	private List<ScriptSource> collectSources() {
		if (getLines() == null) {
			return Collections.emptyList();
		} else {
			return getLines()	.stream()
								.filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
								.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
														.skip(1)
														.map(String::trim))
								.map(PropertiesValueResolver::replaceProperties)
								.flatMap(line -> Util
														.explode(getResourceRef().getOriginalResource(),
																getResourceRef().getFile()
																				.getAbsoluteFile()
																				.getParentFile()
																				.toPath(),
																line)
														.stream())
								.map(resourceRef::asSibling)
								.map(ScriptSource::prepareScript)
								.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	private <R> List<R> collectAll(Function<ScriptSource, List<R>> func) {
		Stream<R> subs = getAllSources().stream().map(s -> func.apply(s).stream()).flatMap(i -> i);
		return Stream.concat(func.apply(this).stream(), subs).collect(Collectors.toList());
	}

	public static ScriptSource prepareScript(String resource) {
		ResourceRef resourceRef = ResourceRef.forResource(resource);
		return prepareScript(resourceRef);
	}

	public static ScriptSource prepareScript(ResourceRef resourceRef) {
		return new ScriptSource(resourceRef);
	}
}

class FileRef {
	final String base;
	final String ref;
	final String destination;

	public FileRef(String base, String ref, String destination) {
		assert (ref != null);
		this.base = base;
		this.ref = ref;
		this.destination = destination;
	}

	private String from() {
		String p = destination != null ? destination : ref;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		return Paths.get(base).resolveSibling(p).toString();
	}

	protected Path to(Path parent) {
		if (Paths.get(ref).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + ref);
		}

		return parent.resolve(ref);
	}

	protected static boolean isURL(String url) {
		try {
			new URL(url);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void copy(Path destroot) {
		Path from = Paths.get(from());
		Path to = to(destroot);
		Util.verboseMsg("Copying " + from + " to " + to);
		try {
			if (!to.toFile().getParentFile().exists()) {
				to.toFile().getParentFile().mkdirs();
			}
			Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Could not copy " + from + " to " + to, ioe);
		}
	}

	public String getRef() {
		return ref;
	}
}

class URLRef extends FileRef {

	public URLRef(String base, String destination, String ref) {
		super(base, destination, ref);
	}

	public String from() {
		String p = destination != null ? destination : ref;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		try {
			return new URI(base).resolve(p).toString();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Could not resolve URI", e);
		}
	}

	public void copy(Path destroot) {
		String from = from();
		Path to = to(destroot);
		Util.verboseMsg("Copying " + from + " to " + to);
		try {
			if (!to.toFile().getParentFile().exists()) {
				to.toFile().getParentFile().mkdirs();
			}
			Path dest = Util.downloadAndCacheFile(Util.swizzleURL(from));
			Files.copy(dest, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Could not copy " + from + " to " + to, ioe);
		}
	}
}
