package dev.jbang;

import java.io.File;
import java.util.*;

public class Jar implements RunUnit {
	private final ScriptResource scriptResource;

	private Jar(ScriptResource resource) {
		this.scriptResource = resource;
	}

	@Override
	public ScriptResource getScriptResource() {
		return scriptResource;
	}

	@Override
	public File getBackingFile() {
		return scriptResource.getFile();
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
		if (DependencyUtil.looksLikeAGav(scriptResource.getOriginalResource())) {
			List<String> dependencies = new ArrayList<>(additionalDeps);
			dependencies.add(scriptResource.getOriginalResource());
			classpath = new DependencyUtil().resolveDependencies(dependencies,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else if (!additionalDeps.isEmpty()) {
			classpath = new DependencyUtil().resolveDependencies(additionalDeps,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else {
			if (getBackingFile() == null) {
				classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getScriptResource().getFile())));
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

	public static Jar prepareJar(ScriptResource scriptResource) {
		return new Jar(scriptResource);
	}
}
