package dev.jbang;

import java.io.File;
import java.util.*;

/**
 * A Jar represents a RunUnit (something runnable) in the form of a JAR file.
 * It's a reference to an already existing JAR file, either as a solitary file
 * on the user's file system or accessible via a URL. But it can also be a Maven
 * GAV (group:artifact:version) reference resolving to a JAR file in the Maven
 * cache (~/.m2/repository).
 *
 * NB: The Jar contains/returns no other information than that which can be
 * extracted from the JAR file itself. So all Jars that refer to the same JAR
 * file will contain/return the exact same information.
 */
public class Jar implements RunUnit {
	private final ResourceRef resourceRef;

	private Jar(ResourceRef resourceRef) {
		this.resourceRef = resourceRef;
	}

	@Override
	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	public File getJar() {
		return getResourceRef().getFile();
	}

	@Override
	public List<String> getAllDependencies(Properties props) {
		return Collections.emptyList();
	}

	@Override
	public ModularClassPath resolveClassPath(List<String> additionalDeps, boolean offline) {
		ModularClassPath classpath;
		if (DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
			List<String> dependencies = new ArrayList<>(additionalDeps);
			dependencies.add(resourceRef.getOriginalResource());
			classpath = new DependencyUtil().resolveDependencies(dependencies,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else if (!additionalDeps.isEmpty()) {
			classpath = new DependencyUtil().resolveDependencies(additionalDeps,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else {
			classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getResourceRef().getFile())));
		}
		return classpath;
	}

	@Override
	public String javaVersion() {
		return null;
	}

	public static Jar prepareJar(ResourceRef resourceRef) {
		return new Jar(resourceRef);
	}
}
