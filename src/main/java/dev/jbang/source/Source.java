package dev.jbang.source;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;

import java.nio.file.Path;
import java.nio.file.Paths;
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

import javax.annotation.Nonnull;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.cli.ResourceNotFoundException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.resolvers.SiblingResourceResolver;
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

	public enum Type {
		java("java"), jshell("jsh"), kotlin("kt"),
		groovy("groovy"), markdown("md");

		public final String extension;

		Type(String extension) {
			this.extension = extension;
		}

		public static List<String> extensions() {
			return Arrays.stream(values()).map(v -> v.extension).collect(Collectors.toList());
		}
	}

	public Source(String contents, Function<String, String> replaceProperties) {
		this(ResourceRef.nullRef, contents, replaceProperties);
	}

	protected Source(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		this(resourceRef, Util.readFileContent(resourceRef.getFile()), replaceProperties);
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

	public Stream<String> getLines() {
		if (lines == null && contents != null) {
			lines = Arrays.asList(contents.split("\\r?\\n"));
		}
		return lines.stream();
	}

	public Stream<String> getTags() {
		// TODO: Insert `.takeWhile(s -> Util.isBlankString(s) || s.startsWith("//"))`
		// once we support at least Java 9
		return getLines().filter(s -> s.startsWith("//"));
	}

	public Optional<String> getJavaPackage() {
		if (contents != null) {
			return Util.getSourcePackage(contents);
		} else {
			return Optional.empty();
		}
	}

	public List<String> collectDependencies() {
		return getLines()	.filter(Source::isDependDeclare)
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
				if (!gav.isEmpty()) { // protects when @Grab might be inside a string (like jbang source)
					return Stream.of(gav);
				} else {
					return Stream.empty();
				}
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
		return getLines()	.filter(Source::isRepoDeclare)
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
		String desc = getTags()	.filter(Source::isDescriptionDeclare)
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
		List<String> gavs = getTags()	.filter(Source::isGavDeclare)
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
		List<String> javaOptions = getTags()
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
		return collectFiles(new SiblingResourceResolver(resourceRef, ResourceResolver.forResources()));
	}

	public List<RefTarget> collectFiles(ResourceResolver siblingResolver) {
		String org = getResourceRef().getOriginalResource();
		Path baseDir = org != null ? getResourceRef().getFile().toAbsolutePath().getParent()
				: Util.getCwd();
		return getTags().filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
						.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
												.skip(1)
												.map(String::trim))
						.map(replaceProperties)
						.flatMap(f -> explodeFileRef(org, baseDir, f).stream())
						.map(f -> toFileRef(f, siblingResolver))
						.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Resolves any globbing characters found in the given
	 * <code>fileReference</code>. A line that has no globbing characters will be
	 * returned unchanged, otherwise a line like `img/**.jpg` or `WEB-INF=web/**`
	 * gets turned into a list of the same value but with the globbing resolved into
	 * actual file paths. In the case that an alias was supplied (eg `WEB-INF=`) it
	 * will be assumed to be the name of the folder the files must be mounted into.
	 * The paths are considered relative to the given <code>baseDir</code>.
	 */
	public static List<String> explodeFileRef(String source, Path baseDir, String fileReference) {
		String[] split = fileReference.split("=", 2);
		if (split.length == 1) {
			List<String> refs = Util.explode(source, baseDir, fileReference);
			return refs	.stream()
						.map(s -> {
							if (Util.isValidPath(s)) {
								Path base = Util.basePathWithoutPattern(fileReference);
								Path sub = base.relativize(Paths.get(s)).getParent();
								if (sub != null) {
									return sub + "/=" + s;
								}
							}
							return s;
						})
						.collect(Collectors.toList());
		} else {
			String filePattern = split[1];
			String alias = !Util.isPattern(filePattern) || split[0].isEmpty() || split[0].endsWith("/") ? split[0]
					: split[0] + "/";
			List<String> refs = Util.explode(source, baseDir, filePattern);
			return refs	.stream()
						.map(s -> {
							if (Util.isValidPath(s)) {
								Path base = Util.basePathWithoutPattern(filePattern);
								Path sub = base.relativize(Paths.get(s)).getParent();
								if (sub != null) {
									return Paths.get(alias).resolve(sub) + "/=" + s;
								}
							}
							return alias + "=" + s;
						})
						.collect(Collectors.toList());
		}
	}

	/**
	 * Turns a reference like `img/logo.jpg` or `WEB-INF/index.html=web/index.html`
	 * into a <code>RefTarget</code>. In case no alias was supplied the
	 * <code>RefTarget</code>'s target will be <code>null</code>. When an alias
	 * terminates in a <code>/</code> the alias will be assumed to be a folder name
	 * and the source's file name will be appended to it, meaning that given
	 * `WEB-INF/=web/index.html` this method will return a <code>RefTarget</code> as
	 * if `WEB-INF/index.html=web/index.html` was supplied.
	 */
	public static RefTarget toFileRef(String fileReference, ResourceResolver siblingResolver) {
		String[] split = fileReference.split("=", 2);
		String src;
		String dest = null;

		if (split.length == 1) {
			src = split[0];
		} else {
			dest = split[0];
			src = split[1];
		}

		Path p = dest != null ? Paths.get(dest) : null;

		if (Paths.get(src).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + src);
		}
		if (p != null && p.isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + dest);
		}

		try {
			ResourceRef ref = siblingResolver.resolve(src);
			if (dest != null && dest.endsWith("/")) {
				p = p.resolve(ref.getFile().getFileName());
			}
			return RefTarget.create(ref, p);
		} catch (ResourceNotFoundException rnfe) {
			throw new ExitException(EXIT_INVALID_INPUT, String.format("Could not find '%s' when resolving '%s' in %s",
					rnfe.getResourceDescription(),
					fileReference,
					siblingResolver.description()),
					rnfe);
		}
	}

	public List<Source> collectSources() {
		return collectSources(new SiblingResourceResolver(resourceRef, ResourceResolver.forResources()));
	}

	public List<Source> collectSources(ResourceResolver siblingResolver) {
		if (getContents() == null) {
			return Collections.emptyList();
		} else {
			String org = getResourceRef().getOriginalResource();
			Path baseDir = org != null ? getResourceRef().getFile().toAbsolutePath().getParent()
					: Util.getCwd();
			return getTags().filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
							.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
													.skip(1)
													.map(String::trim))
							.map(replaceProperties)
							.flatMap(line -> Util.explode(org, baseDir, line).stream())
							.map(ref -> forResource(siblingResolver, ref, null, replaceProperties))
							.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	/**
	 * Creates and returns a new <code>SourceSet</code> that has been initialized
	 * with all relevant information from this <code>Source</code>. Uses a default
	 * resolver that only knows about resources so this can not be used for
	 * compiling code (see <code>RunContext.forResource()</code> for that).
	 *
	 * @return A <code>SourceSet</code>
	 */
	public SourceSet createSourceSet() {
		return createSourceSet(ResourceResolver.forResources());
	}

	/**
	 * Creates and returns a new <code>SourceSet</code> that has been initialized
	 * with all relevant information from this <code>Source</code>.
	 *
	 * @param resolver The resolver to use for dependent (re)sources
	 * @return A <code>SourceSet</code>
	 */
	public SourceSet createSourceSet(ResourceResolver resolver) {
		SourceSet ss = new SourceSet(this);
		ss.setDescription(getDescription().orElse(null));
		ss.setGav(getGav().orElse(null));
		return updateSourceSet(ss, resolver);
	}

	/**
	 * Updates the given <code>SourceSet</code> with all the information from this
	 * <code>Source</code>. This includes the current source file with all other
	 * source files it references, all resource files, anything to do with
	 * dependencies, repositories and class paths as well as compile time and
	 * runtime options.
	 * 
	 * @param ss       The <code>SourceSet</code> to update
	 * @param resolver The resolver to use for dependent (re)sources
	 * @return The given <code>SourceSet</code>
	 */
	@Nonnull
	public SourceSet updateSourceSet(SourceSet ss, ResourceResolver resolver) {
		if (!ss.getSources().contains(getResourceRef())) {
			ss.addSource(this.getResourceRef());
			ss.addResources(collectFiles());
			ss.addDependencies(collectDependencies());
			ss.addRepositories(collectRepositories());
			ss.addCompileOptions(getCompileOptions());
			ss.addRuntimeOptions(getRuntimeOptions());
			collectAgentOptions().forEach(kv -> {
				if (!kv.getKey().isEmpty()) {
					ss.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
				}
			});
			String version = getJavaVersion();
			if (version != null && JavaUtil.checkRequestedVersion(version)) {
				if (new JavaUtil.RequestedVersionComparator().compare(ss.getJavaVersion(), version) > 0) {
					ss.setJavaVersion(version);
				}
			}
			ResourceResolver siblingResolver = new SiblingResourceResolver(getResourceRef(), resolver);
			for (Source includedSource : collectSources(siblingResolver)) {
				includedSource.updateSourceSet(ss, resolver);
			}
		}
		return ss;
	}

	public static Source forResource(String resource, Type forceType, Function<String, String> replaceProperties) {
		return forResource(ResourceResolver.forResources(), resource, forceType, replaceProperties);
	}

	public static Source forResource(ResourceResolver resolver, String resource, Type forceType,
			Function<String, String> replaceProperties) {
		ResourceRef resourceRef = resolver.resolve(resource);
		if (resourceRef == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not find: " + resource);
		}
		return forResourceRef(resourceRef, forceType, replaceProperties);
	}

	public static Source forResourceRef(ResourceRef resourceRef, Type forceType,
			Function<String, String> replaceProperties) {
		String ext = forceType != null ? forceType.extension : resourceRef.getExtension();
		if (ext.equals("kt")) {
			return new KotlinSource(resourceRef, replaceProperties);
		} else if (ext.equals("groovy")) {
			return new GroovySource(resourceRef, replaceProperties);
		} else if (ext.equals("jsh")) {
			return new JshSource(resourceRef, replaceProperties);
		} else if (ext.equals("md")) {
			return MarkdownSource.create(resourceRef, replaceProperties);
		} else {
			return new JavaSource(resourceRef, replaceProperties);
		}
	}
}
