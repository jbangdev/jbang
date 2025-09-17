package dev.jbang.source.parser;

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

import org.jspecify.annotations.NonNull;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class TagReader {
	protected final String contents;
	protected final Function<String, String> propertiesReplacer;

	private static final String REPOS_COMMENT_PREFIX = "REPOS ";
	private static final String DEPS_COMMENT_PREFIX = "DEPS ";
	private static final String FILES_COMMENT_PREFIX = "FILES ";
	private static final String SOURCES_COMMENT_PREFIX = "SOURCES ";
	private static final String MAIN_COMMENT_PREFIX = "MAIN ";
	private static final String MODULE_COMMENT_PREFIX = "MODULE";
	private static final String DESCRIPTION_COMMENT_PREFIX = "DESCRIPTION ";
	private static final String GAV_COMMENT_PREFIX = "GAV ";
	private static final String DOCS_COMMENT_PREFIX = "DOCS ";

	private static final Pattern EOL = Pattern.compile("\\r?\\n");

	public TagReader(String contents, Function<String, String> propertiesReplacer) {
		this.contents = contents;
		this.propertiesReplacer = propertiesReplacer != null ? propertiesReplacer : Function.identity();
	}

	protected String getContents() {
		return contents;
	}

	public abstract Stream<String> getTags();

	public List<String> collectBinaryDependencies() {
		return getTags()
			.filter(this::isDependDeclare)
			.flatMap(this::extractDependencies)
			.map(propertiesReplacer)
			.filter(TagReader::isGav)
			.collect(Collectors.toList());
	}

	public List<String> collectSourceDependencies() {
		return getTags()
			.filter(this::isDependDeclare)
			.flatMap(this::extractDependencies)
			.map(propertiesReplacer)
			.filter(it -> !isGav(it))
			.collect(Collectors.toList());
	}

	protected boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX);
	}

	protected Stream<String> extractDependencies(String line) {
		return Arrays.stream(line.split(" // ")[0].split("[ \t;,]+")).skip(1).map(String::trim);
	}

	private static boolean isGav(String ref) {
		return DependencyUtil.looksLikeAPossibleGav(ref) || !JitPackUtil.ensureGAV(ref).equals(ref);
	}

	public List<MavenRepo> collectRepositories() {
		return getTags()
			.filter(this::isRepoDeclare)
			.flatMap(this::extractRepositories)
			.map(propertiesReplacer)
			.map(DependencyUtil::toMavenRepo)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isRepoDeclare(String line) {
		return line.startsWith(REPOS_COMMENT_PREFIX);
	}

	protected Stream<String> extractRepositories(String line) {
		return Arrays.stream(line.split(" // ")[0].split("[ ;,]+")).skip(1).map(String::trim);
	}

	public List<KeyValue> collectDocs() {
		return getTags()
			.filter(this::isDocsDeclare)
			.map(s -> s.substring(DOCS_COMMENT_PREFIX.length()))
			.map(this::toKeyValue)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isDocsDeclare(String line) {
		return line.startsWith(DOCS_COMMENT_PREFIX);
	}

	public List<KeyValue> collectManifestOptions() {
		return collectRawOptions("MANIFEST").stream()
			.flatMap(TagReader::extractKeyValues)
			.map(this::toKeyValue)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public List<KeyValue> collectAgentOptions() {
		return collectRawOptions("JAVAAGENT").stream()
			.flatMap(TagReader::extractKeyValues)
			.map(this::toKeyValue)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private static Stream<String> extractKeyValues(String line) {
		return Arrays.stream(line.split(" +")).map(String::trim);
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
			.map(String::trim)
			.map(s -> s.substring(MODULE_COMMENT_PREFIX.length()))
			.collect(Collectors.toList());
		if (mods.isEmpty()) {
			return Optional.empty();
		} else {
			if (mods.size() > 1) {
				Util.warnMsg(
						"Multiple //MODULE lines found, only one should be defined in a source file. Using the first");
			}
			if (mods.get(0).isEmpty()) {
				return Optional.of("");
			} else if (Util.isValidModuleIdentifier(mods.get(0).substring(1))) {
				return Optional.of(mods.get(0).substring(1));
			} else {
				throw new IllegalArgumentException(
						"//MODULE line has wrong format, should be '//MODULE [identifier]'");
			}
		}
	}

	protected boolean isModuleDeclare(String line) {
		return line.equals(MODULE_COMMENT_PREFIX) || line.startsWith(MODULE_COMMENT_PREFIX + " ");
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

	@NonNull
	public List<String> collectTags(String... prefixes) {
		List<String> tags;
		if (prefixes.length > 1) {
			tags = new ArrayList<>();
			for (String prefix : prefixes) {
				tags.addAll(collectRawOptions(prefix));
			}
		} else {
			tags = collectRawOptions(prefixes[0]);
		}
		return tags;
	}

	@NonNull
	public List<String> collectOptions(String... prefixes) {
		List<String> options = collectTags(prefixes);
		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return quotedStringToList(options);
	}

	// https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
	static List<String> quotedStringToList(List<String> options) {
		String subjectString = String.join(" ", options);
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

	@NonNull
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
		return collectTags("JAVA");
	}

	public List<String> collectSources() {
		if (getContents() == null) {
			return Collections.emptyList();
		} else {
			return getTags().filter(f -> f.startsWith(SOURCES_COMMENT_PREFIX))
				.flatMap(line -> Arrays.stream(line.split(" // ")[0].split("[ ;,]+"))
					.skip(1)
					.map(String::trim))
				.map(propertiesReplacer)
				.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	public List<KeyValue> collectFiles() {
		return getTags().filter(f -> f.startsWith(FILES_COMMENT_PREFIX))
			.flatMap(line -> Arrays.stream(line.split(" // ")[0].split("[ ;,]+"))
				.skip(1)
				.map(String::trim))
			.map(propertiesReplacer)
			.map(this::toKeyValue)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private KeyValue toKeyValue(String s) {
		KeyValue kv = KeyValue.of(s);
		String value = kv.getValue();
		value = value != null ? propertiesReplacer.apply(value) : null;
		return new KeyValue(kv.getKey(), value);
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
	public static List<String> explodeFileRef(String source, Path baseDir, KeyValue fileReference) {
		if (fileReference.getValue() == null) {
			List<String> refs = Util.explode(source, baseDir, fileReference.getKey());
			return refs.stream()
				.map(s -> {
					if (Util.isValidPath(s)) {
						Path base = Util.basePathWithoutPattern(fileReference.getKey());
						Path sub = base.relativize(Paths.get(s)).getParent();
						if (sub != null) {
							return sub + "/=" + s;
						}
					}
					return s;
				})
				.collect(Collectors.toList());
		} else {
			String fileAlias = fileReference.getKey();
			String filePattern = fileReference.getValue();
			String alias = !Util.isPattern(filePattern) || fileAlias.isEmpty() || fileAlias.endsWith("/") ? fileAlias
					: fileAlias + "/";
			List<String> refs = Util.explode(source, baseDir, filePattern);
			return refs.stream()
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
	 * This class extends the default <code>TagReader</code> with support for Groovy
	 * "grab" annotations.
	 */
	public static class Extended extends TagReader {
		private List<String> tags;

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
		public Stream<String> getTags() {
			if (tags == null) {
				tags = EOL.splitAsStream(contents)
					.filter(s -> s.startsWith("//")
							|| s.contains(DEPS_ANNOT_PREFIX)
							|| s.contains(REPOS_ANNOT_PREFIX))
					.map(s -> s.contains(DEPS_ANNOT_PREFIX)
							|| s.contains(REPOS_ANNOT_PREFIX) ? s : s.substring(2))
					.collect(Collectors.toList());
			}
			return tags.stream();
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
							args.get("classifier"))
						.filter(Objects::nonNull)
						.collect(Collectors.joining(":"));
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
		public Stream<String> getTags() {
			return EOL.splitAsStream(contents).filter(s -> !s.startsWith("//"));
		}

	}
}
