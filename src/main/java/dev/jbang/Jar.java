package dev.jbang;

import java.io.File;
import java.util.*;

public class Jar implements RunUnit {
	private final ResourceRef resourceRef;

	private Jar(ResourceRef resourceRef) {
		this.resourceRef = resourceRef;
	}

	@Override
	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	@Override
	public File getBackingFile() {
		return resourceRef.getFile();
	}

	public File getJar() {
		return getBackingFile();
	}

	@Override
	public List<String> collectAllDependencies(Properties props) {
		return Collections.emptyList();
	}

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
			if (getBackingFile() == null) {
				classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getResourceRef().getFile())));
			} else {
				classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getBackingFile())));
			}
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
