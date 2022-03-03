package dev.jbang.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.sources.*;
import dev.jbang.source.sources.KotlinSource;
import dev.jbang.source.sources.MarkdownSource;
import dev.jbang.util.JavaUtil;
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
public abstract class Source {

	private static final String DEPS_COMMENT_PREFIX = "//DEPS ";
	private static final String FILES_COMMENT_PREFIX = "//FILES ";
	private static final String SOURCES_COMMENT_PREFIX = "//SOURCES ";
	private static final String DESCRIPTION_COMMENT_PREFIX = "//DESCRIPTION ";
	private static final String GAV_COMMENT_PREFIX = "//GAV ";

	private static final String DEPS_ANNOT_PREFIX = "@Grab(";
	private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

	private static final String REPOS_COMMENT_PREFIX = "//REPOS ";
	private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
	private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
	private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile("@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");

	private final ResourceRef resourceRef;
	private final String contents;
	private final Function<String, String> replaceProperties;

	// Cached values
	private List<String> lines;

	public Source(String contents, Function<String, String> replaceProperties) {
		this(ResourceRef.forFile(null), contents, replaceProperties);
	}

	protected Source(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		this(resourceRef, Util.readFileContent(resourceRef.getFile().toPath()), replaceProperties);
	}

	protected Source(ResourceRef resourceRef, String content, Function<String, String> replaceProperties) {
		this.resourceRef = resourceRef;
		this.contents = content;
		this.replaceProperties = replaceProperties != null ? replaceProperties : Function.identity();
	}

	public abstract List<String> getCompileOptions();

	public abstract List<String> getRuntimeOptions();

	public abstract Builder getBuilder(SourceSet ss, RunContext ctx);

	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	public String getContents() {
		return contents;
	}

	public List<String> getLines() {
		if (lines == null && contents != null) {
			lines = Arrays.asList(contents.split("\\r?\\n"));
		}
		return lines;
	}

	public Optional<String> getJavaPackage() {
		if (contents != null) {
			return Util.getSourcePackage(contents);
		} else {
			return Optional.empty();
		}
	}

	public List<String> collectDependencies() {
		// Make sure that dependencies declarations are well formatted
		if (getLines().stream().anyMatch(it -> it.startsWith("// DEPS"))) {
			throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
		}

		return getLines()	.stream()
							.filter(Source::isDependDeclare)
							.flatMap(Source::extractDependencies)
							.map(replaceProperties)
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

	public List<KeyValue> collectAgentOptions() {
		return collectRawOptions("JAVAAGENT")	.stream()
												.flatMap(Source::extractKeyValue)
												.map(Source::toKeyValue)
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
		return !collectAgentOptions().isEmpty();
	}

	public List<MavenRepo> collectRepositories() {
		return getLines()	.stream()
							.filter(Source::isRepoDeclare)
							.flatMap(Source::extractRepositories)
							.map(replaceProperties)
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

	public Optional<String> getDescription() {
		String desc = getLines().stream()
								.filter(Source::isDescriptionDeclare)
								.map(s -> s.substring(DESCRIPTION_COMMENT_PREFIX.length()))
								.collect(Collectors.joining("\n"));
		if (desc.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(desc);
		}
	}

	static boolean isDescriptionDeclare(String line) {
		return line.startsWith(DESCRIPTION_COMMENT_PREFIX);
	}

	public Optional<String> getGav() {
		List<String> gavs = getLines()	.stream()
										.filter(Source::isGavDeclare)
										.map(s -> s.substring(GAV_COMMENT_PREFIX.length()))
										.collect(Collectors.toList());
		if (gavs.isEmpty()) {
			return Optional.empty();
		} else {
			if (gavs.size() > 1) {
				Util.warnMsg(
						"Multiple //GAV lines found, only one should be defined in a source file. Using the first");
			}
			String maybeGav = DependencyUtil.gavWithVersion(gavs.get(0));
			if (!DependencyUtil.looksLikeAGav(maybeGav)) {
				throw new IllegalArgumentException(
						"//GAV line has wrong format, should be '//GAV groupid:artifactid[:version]'");
			}
			return Optional.of(gavs.get(0));
		}
	}

	static boolean isGavDeclare(String line) {
		return line.startsWith(GAV_COMMENT_PREFIX);
	}

	protected List<String> collectOptions(String prefix) {
		List<String> options = collectRawOptions(prefix);

		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return Code.quotedStringToList(String.join(" ", options));
	}

	private List<String> collectRawOptions(String prefix) {
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

	public boolean enableCDS() {
		return !collectRawOptions("CDS").isEmpty();
	}

	public String getJavaVersion() {
		Optional<String> version = collectJavaVersions().stream()
														.filter(JavaUtil::checkRequestedVersion)
														.max(new JavaUtil.RequestedVersionComparator());
		return version.orElse(null);
	}

	private List<String> collectJavaVersions() {
		return collectOptions("JAVA");
	}

	public List<RefTarget> collectFiles() {
		return getLines()	.stream()
							.filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
							.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
													.skip(1)
													.map(String::trim))
							.map(replaceProperties)
							.map(this::toFileRef)
							.collect(Collectors.toCollection(ArrayList::new));
	}

	private RefTarget toFileRef(String fileReference) {
		return RefTarget.create(resourceRef.getOriginalResource(), fileReference);
	}

	public List<Source> collectSources() {
		if (getLines() == null) {
			return Collections.emptyList();
		} else {
			String org = getResourceRef().getOriginalResource();
			Path baseDir = org != null ? getResourceRef().getFile().getAbsoluteFile().getParentFile().toPath()
					: Util.getCwd();
			return getLines()	.stream()
								.filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
								.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
														.skip(1)
														.map(String::trim))
								.map(replaceProperties)
								.flatMap(line -> Util.explode(org, baseDir, line).stream())
								.map(this::getSibling)
								.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	public Source getSibling(String resource) {
		ResourceRef siblingRef = resourceRef.asSibling(resource);
		return forResourceRef(siblingRef, replaceProperties);
	}

	public static Source forResource(String resource, Function<String, String> replaceProperties) {
		ResourceRef resourceRef = ResourceRef.forResource(resource);
		if (resourceRef == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not find: " + resource);
		}
		return forResourceRef(resourceRef, replaceProperties);
	}

	public static Source forResourceRef(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		String originalResource = resourceRef.getOriginalResource();
		if (originalResource != null && originalResource.endsWith(".kt")) {
			return new KotlinSource(resourceRef, replaceProperties);
		}
		if (originalResource != null && originalResource.endsWith(".md")) {
			return MarkdownSource.create(resourceRef, replaceProperties);
		} else if (originalResource != null && originalResource.endsWith(".groovy")) {
			return new GroovySource(resourceRef, replaceProperties);
		} else {
			return new JavaSource(resourceRef, replaceProperties);
		}
	}
}
