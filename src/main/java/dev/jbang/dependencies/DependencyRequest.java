package dev.jbang.dependencies;

import java.util.Collections;

import dev.jbang.util.AttributeParser;

public class DependencyRequest {

	public static final String SCOPE_ATTR = "scope";

	final MavenCoordinate mc;
	final DependencyAttributes attributes;

	public DependencyRequest(MavenCoordinate mc, DependencyAttributes attributes) {
		assert mc != null : "Maven Coordinates shuold never be null";
		assert attributes != null : "Attributes should never be null";
		this.mc = mc;
		this.attributes = attributes;
	}

	public DependencyAttributes getAttributes() {
		return attributes;
	}

	public MavenCoordinate getArtifact() {
		return mc;
	}

	public static DependencyRequest fromString(String input) {

		int firstCurly = input.indexOf("{");
		MavenCoordinate mc;
		try {
			mc = MavenCoordinate.fromString(input.substring(0, firstCurly));
		} catch (IllegalStateException ie) {
			throw new IllegalStateException(String.format("Invalid dependency request: '%s'",
					"Expected format is groupId:artifactId:version[:classifier][@type][{attrlist}]"));
		}
		DependencyAttributes da;
		if (firstCurly >= 0) {
			// TODO: will let "a:b:c:{adsfaf} something else" parse..shuold we fail?
			int lastCurly = input.lastIndexOf("}");

			da = new DependencyAttributes(
					AttributeParser.parseAttributeList(input.substring(firstCurly + 1, lastCurly), SCOPE_ATTR));
		} else {
			da = new DependencyAttributes(Collections.emptyMap());
		}
		return new DependencyRequest(mc, da);
	}
}
