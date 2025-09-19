package dev.jbang.source.parser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.jspecify.annotations.Nullable;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class TagReader {
	public static final String REPOS_COMMENT_PREFIX = "REPOS";
	public static final String DEPS_COMMENT_PREFIX = "DEPS";
	public static final String FILES_COMMENT_PREFIX = "FILES";
	public static final String SOURCES_COMMENT_PREFIX = "SOURCES";
	public static final String COMPILE_OPTIONS_COMMENT_PREFIX = "COMPILE_OPTIONS";
	public static final String JAVAC_OPTIONS_COMMENT_PREFIX = "JAVAC_OPTIONS";
	public static final String NATIVE_OPTIONS_COMMENT_PREFIX = "NATIVE_OPTIONS";
	public static final String RUNTIME_OPTIONS_COMMENT_PREFIX = "RUNTIME_OPTIONS";
	public static final String JAVA_OPTIONS_COMMENT_PREFIX = "JAVA_OPTIONS";
	public static final String MAIN_COMMENT_PREFIX = "MAIN";
	public static final String MODULE_COMMENT_PREFIX = "MODULE";
	public static final String DESCRIPTION_COMMENT_PREFIX = "DESCRIPTION";
	public static final String GAV_COMMENT_PREFIX = "GAV";
	public static final String DOCS_COMMENT_PREFIX = "DOCS";
	public static final String JAVA_COMMENT_PREFIX = "JAVA";
	public static final String JAVAAGENT_COMMENT_PREFIX = "JAVAAGENT";
	public static final String CDS_COMMENT_PREFIX = "CDS";
	public static final String PREVIEW_COMMENT_PREFIX = "PREVIEW";
	public static final String NOINTEGRATIONS_COMMENT_PREFIX = "NOINTEGRATIONS";

	public abstract Stream<Directive> getTags();

	public List<String> collectBinaryDependencies() {
		return getTags()
			.filter(this::isDependDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.filter(TagReader::isGav)
			.collect(Collectors.toList());
	}

	public List<String> collectSourceDependencies() {
		return getTags()
			.filter(this::isDependDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.filter(it -> !isGav(it))
			.collect(Collectors.toList());
	}

	protected boolean isDependDeclare(Directive d) {
		return DEPS_COMMENT_PREFIX.equals(d.getName());
	}

	private static boolean isGav(String ref) {
		return DependencyUtil.looksLikeAPossibleGav(ref) || !JitPackUtil.ensureGAV(ref).equals(ref);
	}

	public List<MavenRepo> collectRepositories() {
		return getTags()
			.filter(this::isRepoDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.map(DependencyUtil::toMavenRepo)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isRepoDeclare(Directive d) {
		return REPOS_COMMENT_PREFIX.equals(d.getName());
	}

	public List<KeyValue> collectDocs() {
		return getTags()
			.filter(this::isDocsDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isDocsDeclare(Directive d) {
		return DOCS_COMMENT_PREFIX.equals(d.getName());
	}

	public Optional<String> getDescription() {
		String desc = getTags()
			.filter(this::isDescriptionDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
			.collect(Collectors.joining("\n"));
		if (desc.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(desc);
		}
	}

	protected boolean isDescriptionDeclare(Directive d) {
		return DESCRIPTION_COMMENT_PREFIX.equals(d.getName());
	}

	public Optional<String> getMain() {
		List<String> mains = getTags()
			.filter(this::isMainDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
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

	protected boolean isMainDeclare(Directive d) {
		return MAIN_COMMENT_PREFIX.equals(d.getName());
	}

	public Optional<String> getModule() {
		List<String> mods = getTags()
			.filter(this::isModuleDeclare)
			.map(d -> d.getValue() != null ? d.getValue() : "")
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
			} else if (Util.isValidModuleIdentifier(mods.get(0))) {
				return Optional.of(mods.get(0));
			} else {
				throw new IllegalArgumentException(
						"//MODULE line has wrong format, should be '//MODULE [identifier]'");
			}
		}
	}

	protected boolean isModuleDeclare(Directive d) {
		return MODULE_COMMENT_PREFIX.equals(d.getName());
	}

	public Optional<String> getGav() {
		List<String> gavs = getTags()
			.filter(this::isGavDeclare)
			.map(Directive::getValue)
			.collect(Collectors.toList());
		if (gavs.isEmpty()) {
			return Optional.empty();
		} else {
			if (gavs.size() > 1) {
				Util.warnMsg(
						"Multiple //GAV lines found, only one should be defined in a source file. Using the first");
			}
			if (gavs.get(0) == null || !DependencyUtil.looksLikeAGav(gavWithVersion(gavs.get(0)))) {
				throw new IllegalArgumentException(
						"//GAV line has wrong format, should be '//GAV groupid:artifactid[:version]'");
			}
			return Optional.of(gavs.get(0));
		}
	}

	protected boolean isGavDeclare(Directive d) {
		return GAV_COMMENT_PREFIX.equals(d.getName());
	}

	private static String gavWithVersion(String gav) {
		if (gav.replaceAll("[^:]", "").length() == 1) {
			gav += ":" + MavenCoordinate.DEFAULT_VERSION;
		}
		return gav;
	}

	public List<KeyValue> collectManifestOptions() {
		return collectTagOptions("MANIFEST");
	}

	public List<KeyValue> collectAgentOptions() {
		return collectTagOptions("JAVAAGENT");
	}

	private List<KeyValue> collectTagOptions(String name) {
		return collectRawOptions(name).stream()
			.filter(Objects::nonNull)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	@NonNull
	public List<String> collectOptions(String... names) {
		List<String> options = collectTags(names);
		// convert quoted content to list of strings as
		// just passing "--enable-preview --source 14" fails
		return quotedStringToList(Q2TL_SPACES, String.join(" ", options));
	}

	@NonNull
	public List<String> collectTags(String... names) {
		List<String> tags;
		if (names.length > 1) {
			tags = new ArrayList<>();
			for (String name : names) {
				tags.addAll(collectRawOptions(name));
			}
		} else {
			tags = collectRawOptions(names[0]);
		}
		return tags;
	}

	@NonNull
	List<String> collectRawOptions(String name) {
		List<String> javaOptions = getTags()
			.filter(d -> d.getName().equals(name))
			.map(Directive::getValue)
			.collect(Collectors.toList());

		String envOptions = System.getenv("JBANG_" + name);
		if (envOptions != null) {
			javaOptions.add(envOptions);
		}
		return javaOptions;
	}

	private static final Pattern Q2TL_SPACES = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

	// SSCT = Spaces Semicolons Commas Tabs
	private static final Pattern Q2TL_SSCT = Pattern.compile("[^\\s;,\"']+|\"([^\"]*)\"|'([^']*)'");

	// https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
	static List<String> quotedStringToList(Pattern regex, String text) {
		if (text == null) {
			return Collections.emptyList();
		}
		List<String> matchList = new ArrayList<>();
		Matcher regexMatcher = regex.matcher(text);
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

	public String getJavaVersion() {
		Optional<String> version = collectJavaVersions().stream()
			.filter(JavaUtil::checkRequestedVersion)
			.max(new JavaUtil.RequestedVersionComparator());
		return version.orElse(null);
	}

	private List<String> collectJavaVersions() {
		return collectTags(JAVA_COMMENT_PREFIX);
	}

	public List<String> collectSources() {
		return getTags()
			.filter(this::isSourcesDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isSourcesDeclare(Directive d) {
		return SOURCES_COMMENT_PREFIX.equals(d.getName());
	}

	public List<KeyValue> collectFiles() {
		return getTags().filter(this::isFilesDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isFilesDeclare(Directive d) {
		return FILES_COMMENT_PREFIX.equals(d.getName());
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

	public static class Directive {
		@NonNull
		private final String name;
		@Nullable
		private final String value;

		public Directive(@NonNull String name, @Nullable String value) {
			this.name = name;
			this.value = value;
		}

		@NonNull
		public String getName() {
			return name;
		}

		@Nullable
		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getValue() != null ? getName() + " " + getValue() : getName();
		}
	}

	private static final Pattern DIRECTIVE = Pattern
		.compile("(?<key>(?:[A-Z]+:)?[A-Z_]+)(?:\\s+(?<value>.*?))?(?:\\s\\/\\/\\s.*)?");

	@Nullable
	protected Directive toDirective(@NonNull String line, @Nullable Function<String, String> propertiesReplacer) {
		Matcher matcher = DIRECTIVE.matcher(line);
		if (matcher.matches()) {
			String value = matcher.group("value");
			if (propertiesReplacer != null && value != null) {
				value = propertiesReplacer.apply(value);
			}
			value = value != null ? value.trim() : null;
			return new Directive(matcher.group("key"), value);
		}
		return null;
	}

	/**
	 * This class extends the default <code>TagReader</code> with support for Groovy
	 * "grab" annotations.
	 */
	public static class Extended extends TagReader {
		private final String contents;
		private final Function<String, String> propertiesReplacer;
		private List<Directive> tags;

		private static final String DEPS_ANNOT_PREFIX = "@Grab(";
		private static final Pattern DEPS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
		private static final Pattern DEPS_ANNOT_SINGLE = Pattern.compile("@Grab\\(\\s*\"(?<value>.*)\"\\s*\\)");

		private static final String REPOS_ANNOT_PREFIX = "@GrabResolver(";
		private static final Pattern REPOS_ANNOT_PAIRS = Pattern.compile("(?<key>\\w+)\\s*=\\s*\"(?<value>.*?)\"");
		private static final Pattern REPOS_ANNOT_SINGLE = Pattern.compile(
				"@GrabResolver\\(\\s*\"(?<value>.*)\"\\s*\\)");

		public Extended(String contents, Function<String, String> propertiesReplacer) {
			this.contents = contents;
			this.propertiesReplacer = propertiesReplacer;
		}

		public static class ExtendedDirective extends Directive {
			public ExtendedDirective(@NonNull String name, @Nullable String value) {
				super(name, value);
			}

			@Override
			public String toString() {
				return "//" + super.toString();
			}
		}

		@Override
		public Stream<Directive> getTags() {
			if (tags == null) {
				tags = Util.stringLines(contents)
					.filter(s -> s.startsWith("//")
							|| s.contains(DEPS_ANNOT_PREFIX)
							|| s.contains(REPOS_ANNOT_PREFIX))
					.map(line -> toDirective(line, propertiesReplacer))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			}
			return tags.stream();
		}

		@Override
		@Nullable
		protected Directive toDirective(@NonNull String line, @Nullable Function<String, String> propertiesReplacer) {
			if (line.contains(DEPS_ANNOT_PREFIX)) {
				return parseDepsAnnotation(line);
			} else if (line.contains(REPOS_ANNOT_PREFIX)) {
				return parseReposAnnotation(line);
			} else {
				Directive d = super.toDirective(line.substring(2), this.propertiesReplacer);
				if (d == null) {
					return null;
				}
				return new ExtendedDirective(d.getName(), d.getValue());
			}
		}

		private Directive parseDepsAnnotation(String line) {
			int commentOrEnd = line.indexOf("//");
			if (commentOrEnd < 0) {
				commentOrEnd = line.length();
			}
			if (line.indexOf(DEPS_ANNOT_PREFIX) > commentOrEnd) {
				// ignore if on line that is a comment
				return null;
			}

			Directive result = null;
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
				if (gav.contains(",")) {
					// GAV contains a comma, likely in a version range, so quote it
					gav = "'" + gav + "'";
				}
				if (!gav.isEmpty()) { // protects when @Grab might be inside a string (like jbang source)
					result = new ExtendedDirective(DEPS_COMMENT_PREFIX, gav);
				}
			} else {
				matcher = DEPS_ANNOT_SINGLE.matcher(line);
				if (matcher.find()) {
					result = new ExtendedDirective(DEPS_COMMENT_PREFIX, matcher.group("value"));
				}
			}
			return result;
		}

		private Directive parseReposAnnotation(String line) {
			int commentOrEnd = line.indexOf("//");
			if (commentOrEnd < 0) {
				commentOrEnd = line.length();
			}
			if (line.indexOf(REPOS_ANNOT_PREFIX) > commentOrEnd) {
				// ignore if on line that is a comment
				return null;
			}

			String repo = null;
			Map<String, String> args = new HashMap<>();
			Matcher matcher = REPOS_ANNOT_PAIRS.matcher(line);
			while (matcher.find()) {
				args.put(matcher.group("key"), matcher.group("value"));
			}
			if (!args.isEmpty()) {
				repo = args.getOrDefault("name", args.get("root")) + "=" + args.get("root");
			} else {
				matcher = REPOS_ANNOT_SINGLE.matcher(line);
				if (matcher.find()) {
					repo = matcher.group("value");
				}
			}
			if (repo == null) {
				return null;
			}
			if (repo.contains(",")) {
				// repo contains a comma (it shouldn't, but it does), so quote it
				repo = "'" + repo + "'";
			}
			return new ExtendedDirective(REPOS_COMMENT_PREFIX, repo);
		}
	}

	public static class JbangProject extends TagReader {
		private final String contents;
		private final Function<String, String> propertiesReplacer;

		private List<Directive> tags;

		public JbangProject(String contents, Function<String, String> propertiesReplacer) {
			this.contents = contents;
			this.propertiesReplacer = propertiesReplacer;
		}

		@Override
		public Stream<Directive> getTags() {
			if (tags == null) {
				tags = Util.stringLines(contents)
					.filter(s -> !s.startsWith("//"))
					.map(line -> toDirective(line, propertiesReplacer))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			}
			return tags.stream();
		}
	}
}
