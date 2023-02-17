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

import dev.jbang.cli.ExitException;
import dev.jbang.cli.ResourceNotFoundException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class TagReader {
	protected final String contents;
	protected final Function<String, String> replaceProperties;

	// Cached values
	private List<String> lines;

	private static final String REPOS_COMMENT_PREFIX = "REPOS ";
	private static final String DEPS_COMMENT_PREFIX = "DEPS ";
	private static final String FILES_COMMENT_PREFIX = "FILES ";
	private static final String SOURCES_COMMENT_PREFIX = "SOURCES ";
	private static final String MAIN_COMMENT_PREFIX = "MAIN ";
	private static final String MODULE_COMMENT_PREFIX = "MODULE ";
	private static final String DESCRIPTION_COMMENT_PREFIX = "DESCRIPTION ";
	private static final String GAV_COMMENT_PREFIX = "GAV ";

	private static final Pattern EOL = Pattern.compile("\\r?\\n");

	public TagReader(String contents, Function<String, String> replaceProperties) {
		this.contents = contents;
		this.replaceProperties = replaceProperties != null ? replaceProperties : Function.identity();
	}

	protected String getContents() {
		return contents;
	}

	protected abstract Stream<String> getTags();

	public List<String> collectDependencies() {
		return getTags()
						.filter(this::isDependDeclare)
						.flatMap(this::extractDependencies)
						.map(replaceProperties)
						.collect(Collectors.toList());
	}

	protected boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX);
	}

	protected Stream<String> extractDependencies(String line) {
		return Arrays.stream(line.split(" // ")[0].split("[ ;,]+")).skip(1).map(String::trim);
	}

	public List<MavenRepo> collectRepositories() {
		return getTags()
						.filter(this::isRepoDeclare)
						.flatMap(this::extractRepositories)
						.map(replaceProperties)
						.map(DependencyUtil::toMavenRepo)
						.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isRepoDeclare(String line) {
		return line.startsWith(REPOS_COMMENT_PREFIX);
	}

	protected Stream<String> extractRepositories(String line) {
		return Arrays.stream(line.split(" // ")[0].split("[ ;,]+")).skip(1).map(String::trim);
	}

	public List<KeyValue> collectManifestOptions() {
		return collectRawOptions("MANIFEST").stream()
											.flatMap(TagReader::extractKeyValues)
											.map(this::toKeyValue)
											.collect(Collectors.toCollection(ArrayList::new));
	}

	public List<KeyValue> collectAgentOptions() {
		return collectRawOptions("JAVAAGENT")	.stream()
												.flatMap(TagReader::extractKeyValues)
												.map(this::toKeyValue)
												.collect(Collectors.toCollection(ArrayList::new));
	}

	private static Stream<String> extractKeyValues(String line) {
		return Arrays.stream(line.split(" +")).map(String::trim);
	}

	private KeyValue toKeyValue(String line) {
		String[] split = line.split("=");
		String key;
		String value = null;

		if (split.length == 1) {
			key = split[0];
		} else if (split.length == 2) {
			key = split[0];
			value = replaceProperties.apply(split[1]);
		} else {
			throw new IllegalStateException("Invalid key/value: " + line);
		}

		return new KeyValue(key, value);
	}

	public Optional<String> getDescription() {
		String desc = getTags()
								.filter(this::isDescriptionDeclare)
								.map(s -> s.substring(DESCRIPTION_COMMENT_PREFIX.length()))
								.collect(Collectors.joining("\n"));
		if (desc.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(desc);
		}
	}

	protected boolean isDescriptionDeclare(String line) {
		return line.startsWith(DESCRIPTION_COMMENT_PREFIX);
	}

	public Optional<String> getMain() {
		List<String> mains = getTags()
										.filter(this::isMainDeclare)
										.map(s -> s.substring(MAIN_COMMENT_PREFIX.length()))
										.collect(Collectors.toList());
		if (mains.isEmpty()) {
			return Optional.empty();
		} else {
			if (mains.size() > 1) {
				Util.warnMsg(
						"Multiple //MAIN lines found, only one should be defined in a source file. Using the first");
			}
			if (!Util.isValidClassIdentifier(mains.get(0))) {
				throw new IllegalArgumentException(
						"//MAIN line has wrong format, should be '//MAIN fullyQualifiedClassName]'");
			}
			return Optional.of(mains.get(0));
		}
	}

	protected boolean isMainDeclare(String line) {
		return line.startsWith(MAIN_COMMENT_PREFIX);
	}

	public Optional<String> getModule() {
		List<String> mods = getTags()
										.filter(this::isModuleDeclare)
										.map(s -> s.substring(MODULE_COMMENT_PREFIX.length()))
										.collect(Collectors.toList());
		if (mods.isEmpty()) {
			return Optional.empty();
		} else {
			if (mods.size() > 1) {
				Util.warnMsg(
						"Multiple //MODULE lines found, only one should be defined in a source file. Using the first");
			}
			if (!Util.isValidModuleIdentifier(mods.get(0))) {
				throw new IllegalArgumentException(
						"//MODULE line has wrong format, should be '//MODULE identifier]'");
			}
			return Optional.of(mods.get(0));
		}
	}

	protected boolean isModuleDeclare(String line) {
		return line.startsWith(MODULE_COMMENT_PREFIX);
	}

	public Optional<String> getGav() {
		List<String> gavs = getTags()
										.filter(this::isGavDeclare)
										.map(s -> s.substring(GAV_COMMENT_PREFIX.length()))
										.collect(Collectors.toList());
		if (gavs.isEmpty()) {
			return Optional.empty();
		} else {
			if (gavs.size() > 1) {
				Util.warnMsg(
						"Multiple //GAV lines found, only one should be defined in a source file. Using the first");
			}
			String maybeGav = gavWithVersion(gavs.get(0));
			if (!DependencyUtil.looksLikeAGav(maybeGav)) {
				throw new IllegalArgumentException(
						"//GAV line has wrong format, should be '//GAV groupid:artifactid[:version]'");
			}
			return Optional.of(gavs.get(0));
		}
	}

	private static String gavWithVersion(String gav) {
		if (gav.replaceAll("[^:]", "").length() == 1) {
			gav += ":" + MavenCoordinate.DEFAULT_VERSION;
		}
		return gav;
	}

	protected boolean isGavDeclare(String line) {
		return line.startsWith(GAV_COMMENT_PREFIX);
	}

	@Nonnull
	public List<String> collectOptions(String... prefixes) {
		List<String> options;
		if (prefixes.length > 1) {
			options = new ArrayList<>();
			for (String prefix : prefixes) {
				options.addAll(collectRawOptions(prefix));
			}
		} else {
			options = collectRawOptions(prefixes[0]);
		}

		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return Project.quotedStringToList(String.join(" ", options));
	}

	@Nonnull
	List<String> collectRawOptions(String prefix) {
		List<String> javaOptions = getTags()
											.map(it -> it.split(" // ")[0]) // strip away nested comments.
											.filter(it -> it.startsWith(prefix + " ")
													|| it.startsWith(prefix + "\t") || it.equals(prefix))
											.map(it -> it.replaceFirst(prefix, "").trim())
											.collect(Collectors.toList());

		String envOptions = System.getenv("JBANG_" + prefix);
		if (envOptions != null) {
			javaOptions.add(envOptions);
		}
		return javaOptions;
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

	public List<Source> collectSources(ResourceRef resourceRef, ResourceResolver siblingResolver) {
		if (getContents() == null) {
			return Collections.emptyList();
		} else {
			String org = resourceRef.getOriginalResource();
			Path baseDir = org != null ? resourceRef.getFile().toAbsolutePath().getParent()
					: Util.getCwd();
			return getTags().filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
							.flatMap(line -> Arrays	.stream(line.split(" // ")[0].split("[ ;,]+"))
													.skip(1)
													.map(String::trim))
							.map(replaceProperties)
							.flatMap(line -> Util.explode(org, baseDir, line).stream())
							.map(ref -> Source.forResource(siblingResolver, ref, replaceProperties))
							.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	public List<RefTarget> collectFiles(ResourceRef resourceRef, ResourceResolver siblingResolver) {
		String org = resourceRef.getOriginalResource();
		Path baseDir = org != null ? resourceRef.getFile().toAbsolutePath().getParent()
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

	/**
	 * This class extends the default <code>TagReader</code> with support for Groovy
	 * "grab" annotations.
	 */
	public static class Extended extends TagReader {
		private static final String DEPS_ANNOT_PREFIX = "@Grab(";
		private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
		private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

		private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
		private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
		private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile(
				"@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");

		public Extended(String contents, Function<String, String> replaceProperties) {
			super(contents, replaceProperties);
		}

		@Override
		protected Stream<String> getTags() {
			return EOL	.splitAsStream(contents)
						.filter(s -> s.startsWith("//")
								|| s.contains(DEPS_ANNOT_PREFIX)
								|| s.contains(REPOS_ANNOT_PREFIX))
						.map(s -> s.contains(DEPS_ANNOT_PREFIX)
								|| s.contains(REPOS_ANNOT_PREFIX) ? s : s.substring(2));
		}

		@Override
		protected boolean isDependDeclare(String line) {
			return line.startsWith(DEPS_COMMENT_PREFIX) || line.contains(DEPS_ANNOT_PREFIX);
		}

		@Override
		protected Stream<String> extractDependencies(String line) {
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

		@Override
		protected boolean isRepoDeclare(String line) {
			return line.startsWith(REPOS_COMMENT_PREFIX) || line.contains(REPOS_ANNOT_PREFIX);
		}

		@Override
		protected Stream<String> extractRepositories(String line) {
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
	}

	public static class JbangProject extends TagReader {

		public JbangProject(String contents, Function<String, String> replaceProperties) {
			super(contents, replaceProperties);
		}

		@Override
		protected Stream<String> getTags() {
			return EOL.splitAsStream(contents).filter(s -> !s.startsWith("//"));
		}

	}
}
