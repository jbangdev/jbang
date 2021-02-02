package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	private String classPath;
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

				String val = attrs.getValue(Source.ATTR_JBANG_JAVA_OPTIONS);
				if (val != null) {
					// should parse it but we are assuming it just gets appended on command line
					// anyway
					javaRuntimeOptions = Collections.singletonList(val);
				}

				String ver = attrs.getValue(Source.ATTR_BUILD_JDK);
				if (ver != null) {
					buildJdk = JavaUtil.parseJavaVersion(ver);
				}

				classPath = attrs.getValue(Attributes.Name.CLASS_PATH);
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
	public ModularClassPath resolveClassPath(List<String> additionalDeps, boolean offline) {
		ModularClassPath mcp;
		if (resourceRef.getOriginalResource() != null
				&& DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
			List<String> dependencies = new ArrayList<>(additionalDeps);
			dependencies.add(resourceRef.getOriginalResource());
			mcp = new DependencyUtil().resolveDependencies(dependencies,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else if (classPath != null) {
			ModularClassPath mcp2 = new DependencyUtil().resolveDependencies(additionalDeps,
					Collections.emptyList(), offline, !Util.isQuiet());
			List<ArtifactInfo> arts = Stream.concat(mcp2.getArtifacts().stream(),
					Stream.of(classPath.split(" ")).map(jar -> new ArtifactInfo(null, new File(jar))))
											.collect(Collectors.toList());
			mcp = new ModularClassPath(arts);
		} else if (!additionalDeps.isEmpty()) {
			mcp = new DependencyUtil().resolveDependencies(additionalDeps,
					Collections.emptyList(), offline, !Util.isQuiet());
		} else {
			mcp = new ModularClassPath(Collections.emptyList());
		}
		return mcp;
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
