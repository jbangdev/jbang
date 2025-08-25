package dev.jbang.dependencies;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 
 * MavenCoordinate should not implement euqals as it is not meaningfull to use
 * as a key in a set as it can have version ranges / different behaviors.
 * 
 * At least at time of writing ModuleUtil had code making the assumption you
 * could look up in set/list of deps. Thus removing it until proven otherwise it
 * is needed/useful.
 * 
 * Use getManagmentKey() if you want to see if dependency exist in list of
 * dpeendencies. Or resolve it fully into an artifact. a
 */
public class MavenCoordinate {
	private final String groupId;
	private final String artifactId;
	private final String version;
	private final String classifier;
	private final String type;

	public static final String DUMMY_GROUP = "group";
	public static final String DEFAULT_VERSION = "999-SNAPSHOT";

	private static final Pattern gavPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*)(:(?<version>[^:@]*))?(:(?<classifier>[^@]*))?(@(?<type>.*))?$");

	private static final Pattern canonicalPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*)((:(?<type>.*)(:(?<classifier>[^@]*))?)?:(?<version>[^:@]*))?$");
	private String managementKey;

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public String getClassifier() {
		return classifier;
	}

	public String getType() {
		return type;
	}

	public static MavenCoordinate fromString(String depId) {
		return parse(depId, gavPattern);
	}

	/**
	 * @return the management key as <code>groupId:artifactId:type</code>
	 *
	 *         This is when want to know what dependency this is about ignoring
	 *         versionrange or any additional properties.
	 */
	public String getManagementKey() {
		if (managementKey == null) {
			managementKey = groupId + ":" + artifactId + ":" + type + (classifier != null ? ":" + classifier : "");
		}
		return managementKey;
	}

	public static MavenCoordinate fromCanonicalString(String depId) {
		return parse(depId, canonicalPattern);
	}

	private static MavenCoordinate parse(String depId, Pattern pattern) {
		Matcher gav = pattern.matcher(depId);
		gav.find();

		if (!gav.matches()) {
			throw new IllegalStateException(String.format(
					"[ERROR] Invalid dependency locator: '%s'.  Expected format is groupId:artifactId:version[:classifier][@type]",
					depId));
		}

		String groupId = gav.group("groupid");
		String artifactId = gav.group("artifactid");
		String version = DependencyUtil.formatVersion(gav.group("version"));
		String classifier = gav.group("classifier");
		String type = Optional.ofNullable(gav.group("type")).orElse("jar");

		return new MavenCoordinate(groupId, artifactId, version, classifier, type);
	}

	public MavenCoordinate(@NonNull String groupId, @NonNull String artifactId, @NonNull String version) {
		this(groupId, artifactId, version, null, null);
	}

	public MavenCoordinate(@NonNull String groupId, @NonNull String artifactId, @NonNull String version,
			@Nullable String classifier, @Nullable String type) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.classifier = classifier != null && classifier.isEmpty() ? null : classifier;
		this.type = type;
	}

	public MavenCoordinate withVersion() {
		return version != null ? this
				: new MavenCoordinate(groupId, artifactId, DEFAULT_VERSION, classifier, type);
	}

	/**
	 * Turns a Maven artifact coordinate into a string. This returns the same format
	 * that can be parsed by the <code>MavenCoordinate.fromString()</code> method.
	 * 
	 * @return stringified version of the coordinate
	 */
	public String toMavenString() {
		String out = groupId + ":" + artifactId;
		if (version != null && !version.isEmpty()) {
			out += ":" + version;
		}
		if (classifier != null && !classifier.isEmpty()) {
			out += ":" + classifier;
		}
		if (type != null && !type.isEmpty()) {
			out += "@" + type;
		}
		return out;
	}

	/**
	 * Turns a Maven artifact coordinate into a string. This is a lossy format that
	 * doesn't follow any known specification and only exists for backward
	 * compatibility with the build integration.
	 * 
	 * @return stringified version of the coordinate
	 */
	public String toCanonicalForm() {
		String out = groupId + ":" + artifactId;
		if (version != null && !version.isEmpty()) {
			if (type != null && !type.isEmpty()) {
				if (classifier != null && !classifier.isEmpty()) {
					out += ":" + classifier;
				}
				out += ":" + type;
			}
			out += ":" + version;
		}
		return out;
	}

	@Override
	public String toString() {
		return "MavenCoordinate{" +
				"groupId='" + groupId + '\'' +
				", artifactId='" + artifactId + '\'' +
				", version='" + version + '\'' +
				", classifier='" + classifier + '\'' +
				", type='" + type + '\'' +
				'}';
	}
}
