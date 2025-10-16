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
import org.jspecify.annotations.Nullable;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class Directives {
	public abstract static class Names {
		public static final String CDS = "CDS";
		public static final String COMPILE_OPTIONS = "COMPILE_OPTIONS";
		public static final String DEPS = "DEPS";
		public static final String DESCRIPTION = "DESCRIPTION";
		public static final String DOCS = "DOCS";
		public static final String FILES = "FILES";
		public static final String GAV = "GAV";
		public static final String JAVAAGENT = "JAVAAGENT";
		public static final String JAVAC_OPTIONS = "JAVAC_OPTIONS";
		public static final String JAVA = "JAVA";
		public static final String JAVA_OPTIONS = "JAVA_OPTIONS";
		public static final String MAIN = "MAIN";
		public static final String MANIFEST = "MANIFEST";
		public static final String MODULE = "MODULE";
		public static final String NATIVE_OPTIONS = "NATIVE_OPTIONS";
		public static final String NOINTEGRATIONS = "NOINTEGRATIONS";
		public static final String PREVIEW = "PREVIEW";
		public static final String REPOS = "REPOS";
		public static final String RUNTIME_OPTIONS = "RUNTIME_OPTIONS";
		public static final String SOURCES = "SOURCES";

		// Directives introduced by non-Java source types extensions
		public static final String GROOVY = "GROOVY";
		public static final String KOTLIN = "KOTLIN";
	}

	public abstract Stream<Directive> getAll();

	public List<String> binaryDependencies() {
		return getAll()
			.filter(this::isDependDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.filter(Directives::isGav)
			.collect(Collectors.toList());
	}

	public List<String> sourceDependencies() {
		return getAll()
			.filter(this::isDependDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.filter(it -> !isGav(it))
			.collect(Collectors.toList());
	}

	protected boolean isDependDeclare(Directive d) {
		return Names.DEPS.equals(d.getName());
	}

	private static boolean isGav(String ref) {
		return DependencyUtil.looksLikeAPossibleGav(ref) || JitPackUtil.possibleMatch(ref);
	}

	public List<MavenRepo> repositories() {
		return getAll()
			.filter(this::isRepoDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.map(DependencyUtil::toMavenRepo)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isRepoDeclare(Directive d) {
		return Names.REPOS.equals(d.getName());
	}

	public List<KeyValue> collectDocs() {
		return getAll()
			.filter(this::isDocsDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isDocsDeclare(Directive d) {
		return Names.DOCS.equals(d.getName());
	}

	public String description() {
		String desc = getAll()
			.filter(this::isDescriptionDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
			.collect(Collectors.joining("\n"));
		return desc.isEmpty() ? null : desc;
	}

	protected boolean isDescriptionDeclare(Directive d) {
		return Names.DESCRIPTION.equals(d.getName());
	}

	public String mainMethod() {
		List<String> mains = getAll()
			.filter(this::isMainDeclare)
			.map(Directive::getValue)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		if (mains.isEmpty()) {
			return null;
		} else {
			if (mains.size() > 1) {
				Util.warnMsg(
						"Multiple //MAIN lines found, only one should be defined in a source file. Using the first");
			}
			if (!Util.isValidClassIdentifier(mains.get(0))) {
				throw new IllegalArgumentException(
						"//MAIN line has wrong format, should be '//MAIN fullyQualifiedClassName]'");
			}
			return mains.get(0);
		}
	}

	protected boolean isMainDeclare(Directive d) {
		return Names.MAIN.equals(d.getName());
	}

	public String module() {
		List<String> mods = getAll()
			.filter(this::isModuleDeclare)
			.map(d -> d.getValue() != null ? d.getValue() : "")
			.collect(Collectors.toList());
		if (mods.isEmpty()) {
			return null;
		} else {
			if (mods.size() > 1) {
				Util.warnMsg(
						"Multiple //MODULE lines found, only one should be defined in a source file. Using the first");
			}
			if (mods.get(0).isEmpty()) {
				return "";
			} else if (Util.isValidModuleIdentifier(mods.get(0))) {
				return mods.get(0);
			} else {
				throw new IllegalArgumentException(
						"//MODULE line has wrong format, should be '//MODULE [identifier]'");
			}
		}
	}

	protected boolean isModuleDeclare(Directive d) {
		return Names.MODULE.equals(d.getName());
	}

	public String gav() {
		List<String> gavs = getAll()
			.filter(this::isGavDeclare)
			.map(Directive::getValue)
			.collect(Collectors.toList());
		if (gavs.isEmpty()) {
			return null;
		} else {
			if (gavs.size() > 1) {
				Util.warnMsg(
						"Multiple //GAV lines found, only one should be defined in a source file. Using the first");
			}
			if (gavs.get(0) == null || !DependencyUtil.looksLikeAGav(gavWithVersion(gavs.get(0)))) {
				throw new IllegalArgumentException(
						"//GAV line has wrong format, should be '//GAV groupid:artifactid[:version]'");
			}
			return gavs.get(0);
		}
	}

	protected boolean isGavDeclare(Directive d) {
		return Names.GAV.equals(d.getName());
	}

	private static String gavWithVersion(String gav) {
		if (gav.replaceAll("[^:]", "").length() == 1) {
			gav += ":" + MavenCoordinate.DEFAULT_VERSION;
		}
		return gav;
	}

	public List<KeyValue> manifestOptions() {
		return collectDirectiveOptions(Names.MANIFEST);
	}

	public List<KeyValue> agentOptions() {
		return collectDirectiveOptions(Names.JAVAAGENT);
	}

	public boolean isAgent() {
		return collectDirectives(Names.JAVAAGENT).anyMatch(d -> true);
	}

	public boolean enableCDS() {
		return collectDirectives(Names.CDS).anyMatch(d -> true);
	}

	public boolean enablePreview() {
		return collectDirectives(Names.PREVIEW).anyMatch(d -> true);
	}

	public boolean disableIntegrations() {
		return collectDirectives(Names.NOINTEGRATIONS).anyMatch(d -> true);
	}

	private List<KeyValue> collectDirectiveOptions(String name) {
		return collectOptions(name)
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	@NonNull
	public List<String> compileOptions() {
		return collectOptions(Names.COMPILE_OPTIONS, Names.JAVAC_OPTIONS)
			.collect(Collectors.toList());
	}

	@NonNull
	public List<String> runtimeOptions() {
		return collectOptions(Names.RUNTIME_OPTIONS, Names.JAVA_OPTIONS)
			.collect(Collectors.toList());
	}

	@NonNull
	public List<String> nativeOptions() {
		return collectOptions(Names.NATIVE_OPTIONS).collect(Collectors.toList());
	}

	@NonNull
	public Stream<String> collectOptions(String... names) {
		return collectDirectives(names)
			.filter(Objects::nonNull)
			.flatMap(v -> quotedStringToList(Q2TL_SPACES, v).stream());
	}

	@NonNull
	public Stream<String> collectDirectives(String... names) {
		return Arrays.stream(names).flatMap(this::collectDirective);
	}

	@NonNull
	protected Stream<String> collectDirective(String name) {
		Stream<String> javaOptions = getAll()
			.filter(d -> d.getName().equals(name))
			.map(Directive::getValue);

		String envOptions = System.getenv("JBANG_" + name);
		if (envOptions != null) {
			javaOptions = Stream.concat(javaOptions, Stream.of(envOptions));
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

	public String javaVersion() {
		Optional<String> version = collectJavaVersions().stream()
			.filter(JavaUtil::checkRequestedVersion)
			.max(new JavaUtil.RequestedVersionComparator());
		return version.orElse(null);
	}

	private List<String> collectJavaVersions() {
		return collectDirectives(Names.JAVA).collect(Collectors.toList());
	}

	public List<String> sources() {
		return getAll()
			.filter(this::isSourcesDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isSourcesDeclare(Directive d) {
		return Names.SOURCES.equals(d.getName());
	}

	public List<KeyValue> files() {
		return getAll().filter(this::isFilesDeclare)
			.map(Directive::getValue)
			.flatMap(v -> quotedStringToList(Q2TL_SSCT, v).stream())
			.map(KeyValue::of)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	protected boolean isFilesDeclare(Directive d) {
		return Names.FILES.equals(d.getName());
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
	 * This class extends the default <code>Directives</code> with support for
	 * Groovy "grab" annotations.
	 */
	public static class Extended extends Directives {
		private String contents;
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
		public Stream<Directive> getAll() {
			if (tags == null) {
				tags = Util.stringLines(contents)
					.filter(s -> s.startsWith("//")
							|| s.contains(DEPS_ANNOT_PREFIX)
							|| s.contains(REPOS_ANNOT_PREFIX))
					.map(line -> toDirective(line, propertiesReplacer))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
				contents = null;
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
					result = new ExtendedDirective(Names.DEPS, gav);
				}
			} else {
				matcher = DEPS_ANNOT_SINGLE.matcher(line);
				if (matcher.find()) {
					result = new ExtendedDirective(Names.DEPS, matcher.group("value"));
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
			return new ExtendedDirective(Names.REPOS, repo);
		}
	}

	public static class JbangProject extends Directives {
		private String contents;
		private final Function<String, String> propertiesReplacer;

		private List<Directive> tags;

		public JbangProject(String contents, Function<String, String> propertiesReplacer) {
			this.contents = contents;
			this.propertiesReplacer = propertiesReplacer;
		}

		@Override
		public Stream<Directive> getAll() {
			if (tags == null) {
				tags = Util.stringLines(contents)
					.filter(s -> !s.startsWith("//"))
					.map(line -> toDirective(line, propertiesReplacer))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
				contents = null;
			}
			return tags.stream();
		}
	}
}
