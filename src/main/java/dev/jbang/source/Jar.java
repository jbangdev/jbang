package dev.jbang.source;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.source.builders.BaseBuilder;
import dev.jbang.source.generators.JarCmdGenerator;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

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
public class Jar implements Code {
	private final ResourceRef resourceRef;
	private final File jarFile;

	// Values read from MANIFEST
	private String classPath;
	private String mainClass;
	private List<String> javaRuntimeOptions;
	private int buildJdk;

	// Cached values
	private SourceSet sourceSet;

	private Jar(ResourceRef resourceRef, File jar) {
		this.resourceRef = resourceRef;
		this.jarFile = jar;
		this.javaRuntimeOptions = Collections.emptyList();
		if (jar.exists()) {
			try (JarFile jf = new JarFile(jar)) {
				Attributes attrs = jf.getManifest().getMainAttributes();
				mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);

				String val = attrs.getValue(BaseBuilder.ATTR_JBANG_JAVA_OPTIONS);
				if (val != null) {
					javaRuntimeOptions = Code.quotedStringToList(val);
				}

				String ver = attrs.getValue(BaseBuilder.ATTR_BUILD_JDK);
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

	@Override
	public File getJarFile() {
		return jarFile;
	}

	@Override
	public Jar asJar() {
		return this;
	}

	@Override
	public SourceSet asSourceSet() {
		if (sourceSet == null) {
			sourceSet = SourceSet.forSource(Source.forResourceRef(resourceRef, null));
			sourceSet.setMainClass(getMainClass());
			sourceSet.addRuntimeOptions(getRuntimeOptions());
			sourceSet.setJavaVersion(getJavaVersion().orElse(null));
			// TODO deduplicate with code from updateDependencyResolver()
			if (resourceRef.getOriginalResource() != null
					&& DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
				sourceSet.addDependency(resourceRef.getOriginalResource());
			} else if (classPath != null) {
				sourceSet.addClassPaths(Arrays.asList(classPath.split(" ")));
			}
		}
		return sourceSet;
	}

	/**
	 * Determines if the associated jar is up-to-date, returns false if it needs to
	 * be rebuilt
	 */
	public boolean isUpToDate() {
		return jarFile != null && jarFile.exists()
				&& updateDependencyResolver(new DependencyResolver()).resolve().isValid();
	}

	@Override
	public DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		if (resourceRef.getOriginalResource() != null
				&& DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
			resolver.addDependency(resourceRef.getOriginalResource());
		} else if (classPath != null) {
			resolver.addClassPaths(Arrays.asList(classPath.split(" ")));
		}
		return resolver;
	}

	@Override
	public Optional<String> getJavaVersion() {
		return Optional.of(buildJdk + "+");
	}

	@Override
	public String getMainClass() {
		return mainClass;
	}

	@Override
	public List<String> getRuntimeOptions() {
		return javaRuntimeOptions;
	}

	@Override
	public CmdGenerator cmdGenerator(RunContext ctx) {
		return new JarCmdGenerator(this, ctx);
	}

	public static Jar prepareJar(ResourceRef resourceRef) {
		return new Jar(resourceRef, resourceRef.getFile());
	}

	public static Jar prepareJar(ResourceRef resourceRef, File jarFile) {
		return new Jar(resourceRef, jarFile);
	}

	public static Jar prepareJar(SourceSet ss) {
		Jar jsrc = new Jar(ss.getResourceRef(), ss.getJarFile());
		jsrc.sourceSet = ss;
		return jsrc;
	}
}
