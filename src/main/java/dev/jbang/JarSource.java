package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * A Jar represents a Source (something runnable) in the form of a JAR file.
 * It's a reference to an already existing JAR file, either as a solitary file
 * on the user's file system or accessible via a URL. But it can also be a Maven
 * GAV (group:artifact:version) reference resolving to a JAR file in the Maven
 * cache (~/.m2/repository).
 *
 * NB: The Jar contains/returns no other information than that which can be
 * extracted from the JAR file itself. So all Jars that refer to the same JAR
 * file will contain/return the exact same information.
 */
public class JarSource implements Source {
	private final ResourceRef resourceRef;

	private String mainClass;
	private List<String> javaRuntimeOptions;
	private int buildJdk;

	private JarSource(ResourceRef resourceRef) {
		this.resourceRef = resourceRef;
		this.javaRuntimeOptions = Collections.emptyList();
		File jar = getResourceRef().getFile();
		if (jar.exists()) {
			try (JarFile jf = new JarFile(jar)) {
				Attributes attrs = jf.getManifest().getMainAttributes();
				mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);

				String val = attrs.getValue(ScriptSource.JBANG_JAVA_OPTIONS);
				if (val != null) {
					// should parse it but we are assuming it just gets appended on command line
					// anyway
					javaRuntimeOptions = Collections.singletonList(val);
				}

				String ver = attrs.getValue(ScriptSource.BUILD_JDK);
				if (ver != null) {
					buildJdk = JavaUtil.parseJavaVersion(ver);
				}
			} catch (IOException e) {
				Util.warnMsg("Problem reading manifest from " + getResourceRef().getFile());
			}
		}
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
	public ModularClassPath resolveClassPath(DependencyContext additionalDependencyContext, boolean offline) {
		ModularClassPath classpath;
		if (DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
			List<String> dependencies = new ArrayList<>(additionalDependencyContext.getDependencies());
			dependencies.add(resourceRef.getOriginalResource());
			classpath = new DependencyUtil().resolveDependencies(dependencies,
					additionalDependencyContext.getRepositoriesAsMavenRepo(),
					offline, !Util.isQuiet());
		} else if (!additionalDependencyContext.getDependencies().isEmpty()) {
			classpath = new DependencyUtil().resolveDependencies(additionalDependencyContext.getDependencies(),
					additionalDependencyContext.getRepositoriesAsMavenRepo(), offline, !Util.isQuiet());
		} else {
			classpath = new ModularClassPath(
					Collections.singletonList(new ArtifactInfo(null, getResourceRef().getFile())));
		}
		return classpath;
	}

	@Override
	public String javaVersion() {
		return null;
	}

	@Override
	public String getMainClass() {
		return mainClass;
	}

	@Override
	public List<String> getRuntimeOptions() {
		return javaRuntimeOptions;
	}

	public int getBuildJdk() {
		return buildJdk;
	}

	public static JarSource prepareJar(ResourceRef resourceRef) {
		return new JarSource(resourceRef);
	}
}
