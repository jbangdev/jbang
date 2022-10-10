package dev.jbang.source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.*;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.generators.JarCmdGenerator;
import dev.jbang.source.generators.JshCmdGenerator;
import dev.jbang.source.generators.NativeCmdGenerator;
import dev.jbang.source.resolvers.*;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

/**
 * This class constructs a <code>Project</code>. It uses the options given by
 * the user on the command line or things that are part of the user's
 * environment.
 */
public class ProjectBuilder {
	private List<String> additionalSources = Collections.emptyList();
	private List<String> additionalResources = Collections.emptyList();
	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalRepos = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private Map<String, String> properties = Collections.emptyMap();
	private Source.Type forceType = null;
	private String mainClass;
	private List<Project> javaAgents;
	private File catalogFile;

	private ModularClassPath mcp;
	private boolean nativeImage;
	private String javaVersion;
	private Properties contextProperties;

	private List<String> arguments = Collections.emptyList();
	private List<String> javaOptions = Collections.emptyList();
	private boolean interactive;
	private boolean enableAssertions;
	private boolean enableSystemAssertions;
	private String flightRecorderString;
	private String debugString;
	private Boolean classDataSharing;
	private Path buildDir;

	public static ProjectBuilder create() {
		return new ProjectBuilder();
	}

	private ProjectBuilder() {
	}

	// TODO Try to get rid of this getter
	public List<String> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	public ProjectBuilder setArguments(List<String> arguments) {
		if (arguments != null) {
			this.arguments = new ArrayList<>(arguments);
		} else {
			this.arguments = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder setProperties(Map<String, String> properties) {
		if (properties != null) {
			this.properties = properties;
		} else {
			this.properties = Collections.emptyMap();
		}
		return this;
	}

	public ProjectBuilder interactive(boolean interactive) {
		this.interactive = interactive;
		return this;
	}

	public ProjectBuilder enableAssertions(boolean enableAssertions) {
		this.enableAssertions = enableAssertions;
		return this;
	}

	public ProjectBuilder enableSystemAssertions(boolean enableSystemAssertions) {
		this.enableSystemAssertions = enableSystemAssertions;
		return this;
	}

	public ProjectBuilder flightRecorderString(String flightRecorderString) {
		this.flightRecorderString = flightRecorderString;
		return this;
	}

	public ProjectBuilder debugString(String debugString) {
		this.debugString = debugString;
		return this;
	}

	public ProjectBuilder classDataSharing(Boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
		return this;
	}

	public ProjectBuilder additionalSources(List<String> sources) {
		if (sources != null) {
			this.additionalSources = new ArrayList<>(sources);
		} else {
			this.additionalSources = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder additionalResources(List<String> resources) {
		if (resources != null) {
			this.additionalResources = new ArrayList<>(resources);
		} else {
			this.additionalResources = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder additionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
		mcp = null;
		return this;
	}

	public ProjectBuilder additionalRepositories(List<String> repos) {
		if (repos != null) {
			this.additionalRepos = repos;
		} else {
			this.additionalRepos = Collections.emptyList();
		}
		mcp = null;
		return this;
	}

	public ProjectBuilder additionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
		mcp = null;
		return this;
	}

	public ProjectBuilder forceType(Source.Type forceType) {
		this.forceType = forceType;
		return this;
	}

	public ProjectBuilder mainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public ProjectBuilder javaOptions(List<String> javaOptions) {
		if (javaOptions != null) {
			this.javaOptions = javaOptions;
		} else {
			this.javaOptions = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder nativeImage(boolean nativeImage) {
		this.nativeImage = nativeImage;
		return this;
	}

	public ProjectBuilder javaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	public ProjectBuilder catalog(File catalogFile) {
		this.catalogFile = catalogFile;
		return this;
	}

	public ProjectBuilder buildDir(Path buildDir) {
		this.buildDir = buildDir;
		return this;
	}

	public ProjectBuilder addJavaAgent(Project prj) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(prj);
		return this;
	}

	private Properties getContextProperties() {
		if (contextProperties == null) {
			contextProperties = getContextProperties(properties);
		}
		return contextProperties;
	}

	public static Properties getContextProperties(Map<String, String> properties) {
		Properties contextProperties = new Properties(System.getProperties());
		// early/eager init to property resolution will work.
		new Detector().detect(contextProperties, Collections.emptyList());

		contextProperties.putAll(properties);
		return contextProperties;
	}

	private void updateDependencyResolver(DependencyResolver resolver) {
		resolver
				.addRepositories(allToMavenRepo(replaceAllProps(
						additionalRepos)))
				.addDependencies(replaceAllProps(
						additionalDeps))
				.addClassPaths(
						replaceAllProps(additionalClasspaths));
	}

	private List<String> replaceAllProps(List<String> items) {
		return items.stream()
					.map(item -> PropertiesValueResolver.replaceProperties(item, getContextProperties()))
					.collect(Collectors.toList());
	}

	private List<MavenRepo> allToMavenRepo(List<String> repos) {
		return repos.stream().map(DependencyUtil::toMavenRepo).collect(Collectors.toList());

	}

	public Project build(String resource) {
		ResourceRef resourceRef = resolveChecked(getResourceResolver(), resource);
		return build(resourceRef);
	}

	private static ResourceRef resolveChecked(ResourceResolver resolver, String resource) {
		ResourceRef ref = resolver.resolve(resource);
		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (ref == null || !Files.isReadable(ref.getFile())) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Script or alias could not be found or read: '" + resource + "'");
		}
		return ref;
	}

	// Only used by tests
	public Project build(Path resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return build(resourceRef);
	}

	public Project build(ResourceRef resourceRef) {
		Project prj;
		if (resourceRef.getFile().getFileName().toString().endsWith(".jar")) {
			prj = createJarProject(resourceRef);
		} else if (Util.isPreview()
				&& resourceRef.getFile().getFileName().toString().equals(Project.BuildFile.jbang.fileName)) {
			// This is a bit of a hack, but what we do here is treat "build.jbang"
			// as if it were a source file, which we can do because it's syntax is
			// the same, just that it can only contain //-lines but no code.
			prj = createSourceProject(resourceRef);
			// But once we get the resulting <code>Project</code> we remove the
			// first file from the sources to prevent "build.jbang" from being
			// passed to the compiler.
			prj.getMainSourceSet().removeSource(prj.getMainSourceSet().getSources().get(0));
			prj.setMainSource(createSource(prj.getMainSourceSet().getSources().get(0)));
		} else {
			prj = createSourceProject(resourceRef);
		}
		return prj;
	}

	private Project createJarProject(ResourceRef resourceRef) {
		return updateProject(importJarMetadata(new Project(resourceRef)));
	}

	private Project createSourceProject(ResourceRef resourceRef) {
		Project prj = createSource(resourceRef).createProject(getResourceResolver());
		return updateProject(importJarMetadata(prj));
	}

	private Source createSource(ResourceRef resourceRef) {
		return Source.forResourceRef(resourceRef,
				it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));

	}

	private Project importJarMetadata(Project prj) {
		ResourceRef resourceRef = prj.getResourceRef();
		prj.setCacheDir(buildDir);
		Path jar = prj.getJarFile();
		if (jar != null && Files.exists(jar)) {
			try (JarFile jf = new JarFile(jar.toFile())) {
				Attributes attrs = jf.getManifest().getMainAttributes();
				prj.setMainClass(attrs.getValue(Attributes.Name.MAIN_CLASS));

				Optional<JarEntry> pom = jf.stream().filter(e -> e.getName().endsWith("/pom.xml")).findFirst();
				if (pom.isPresent()) {
					try (InputStream is = jf.getInputStream(pom.get())) {
						MavenXpp3Reader reader = new MavenXpp3Reader();
						Model model = reader.read(is);
						// GAVS of the form "group:xxxx:999-SNAPSHOT" are skipped
						if (!MavenCoordinate.DUMMY_GROUP.equals(model.getGroupId())
								|| !MavenCoordinate.DEFAULT_VERSION.equals(model.getVersion())) {
							String gav = model.getGroupId() + ":" + model.getArtifactId();
							// The version "999-SNAPSHOT" is ignored
							if (!MavenCoordinate.DEFAULT_VERSION.equals(model.getVersion())) {
								gav += ":" + model.getVersion();
							}
							prj.setGav(gav);
						}
					} catch (XmlPullParserException e) {
						Util.verboseMsg("Unable to read the JAR's pom.xml file", e);
					}
				}

				// TODO should be removed
				// String val = attrs.getValue(BaseBuilder.ATTR_JBANG_JAVA_OPTIONS);
				// if (val != null) {
				// prj.addRuntimeOptions(Project.quotedStringToList(val));
				// }

				String ver = attrs.getValue(JarBuildStep.ATTR_BUILD_JDK);
				if (ver != null) {
					// buildJdk = JavaUtil.parseJavaVersion(ver);
					prj.setJavaVersion(JavaUtil.parseJavaVersion(ver) + "+");
				}

				String classPath = attrs.getValue(Attributes.Name.CLASS_PATH);
				if (resourceRef.getOriginalResource() != null
						&& DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
					prj.getMainSourceSet().addDependency(resourceRef.getOriginalResource());
				} else if (classPath != null) {
					prj.getMainSourceSet().addClassPaths(Arrays.asList(classPath.split(" ")));
				}
			} catch (IOException e) {
				Util.warnMsg("Problem reading manifest from " + jar);
			}
		}
		return prj;
	}

	private CmdGenerator createCmdGenerator(Project prj) {
		if (prj.isJShell() || forceType == Source.Type.jshell || interactive) {
			return createJshCmdGenerator(prj);
		} else {
			if (nativeImage) {
				return createNativeCmdGenerator(prj);
			} else {
				return createJarCmdGenerator(prj);
			}
		}
	}

	private JarCmdGenerator createJarCmdGenerator(Project prj) {
		return new JarCmdGenerator(prj)
										.arguments(arguments)
										.mainRequired(!interactive)
										.assertions(enableAssertions)
										.systemAssertions(enableSystemAssertions)
										.classDataSharing(Optional.ofNullable(classDataSharing).orElse(false))
										.debugString(debugString)
										.flightRecorderString(flightRecorderString);
	}

	private JshCmdGenerator createJshCmdGenerator(Project prj) {
		return new JshCmdGenerator(prj)
										.arguments(arguments)
										.interactive(interactive)
										.debugString(debugString)
										.flightRecorderString(flightRecorderString);
	}

	private NativeCmdGenerator createNativeCmdGenerator(Project prj) {
		return new NativeCmdGenerator(prj, createJarCmdGenerator(prj))
																		.arguments(arguments);
	}

	private Project updateProject(Project prj) {
		SourceSet ss = prj.getMainSourceSet();
		prj.addRepositories(allToMavenRepo(replaceAllProps(additionalRepos)));
		ss.addDependencies(replaceAllProps(additionalDeps));
		ss.addClassPaths(replaceAllProps(additionalClasspaths));
		updateAllSources(prj, replaceAllProps(additionalSources));
		ss.addResources(allToFileRef(replaceAllProps(additionalResources)));
		prj.putProperties(properties);
		prj.addRuntimeOptions(javaOptions);
		prj.addJavaAgents(javaAgents);
		if (mainClass != null) {
			prj.setMainClass(mainClass);
		}
		if (javaVersion != null) {
			prj.setJavaVersion(javaVersion);
		}
		prj.setNativeImage(nativeImage);
		prj.setCmdGeneratorFactory(() -> createCmdGenerator(prj));
		return prj;
	}

	private void updateAllSources(Project prj, List<String> sources) {
		Catalog catalog = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		ResourceResolver resolver = getResourceResolver();
		sources	.stream()
				.flatMap(f -> Util.explode(null, Util.getCwd(), f).stream())
				.map(s -> resolveChecked(resolver, s))
				.map(this::createSource)
				.forEach(src -> src.updateProject(prj, resolver));
	}

	private List<RefTarget> allToFileRef(List<String> resources) {
		ResourceResolver resolver = ResourceResolver.forResources();
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		return resources.stream()
						.flatMap(f -> TagReader.explodeFileRef(null, Util.getCwd(), f).stream())
						.map(f -> TagReader.toFileRef(f, resolver))
						.collect(Collectors.toList());
	}

	private ResourceResolver getResourceResolver() {
		Catalog cat = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		return new AliasResourceResolver(cat, this::getAliasResourceResolver);
	}

	private ResourceResolver getAliasResourceResolver(Alias alias) {
		if (alias != null) {
			updateFromAlias(alias);
		}
		return new CombinedResourceResolver(
				new RenamingScriptResourceResolver(forceType),
				new LiteralScriptResourceResolver(),
				new RemoteResourceResolver(false),
				new ClasspathResourceResolver(),
				new GavResourceResolver(this::resolveDependency),
				new FileResourceResolver());
	}

	private ModularClassPath resolveDependency(String dep) {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver().addDependency(dep);
			updateDependencyResolver(resolver);
			mcp = resolver.resolve();
		}
		return mcp;
	}

	private void updateFromAlias(Alias alias) {
		if (arguments.isEmpty()) {
			setArguments(alias.arguments);
		}
		if (javaOptions.isEmpty()) {
			javaOptions(alias.javaOptions);
		}
		if (additionalSources.isEmpty()) {
			additionalSources(alias.sources);
		}
		if (additionalResources.isEmpty()) {
			additionalResources(alias.resources);
		}
		if (additionalDeps.isEmpty()) {
			additionalDependencies(alias.dependencies);
		}
		if (additionalRepos.isEmpty()) {
			additionalRepositories(alias.repositories);
		}
		if (additionalClasspaths.isEmpty()) {
			additionalClasspaths(alias.classpaths);
		}
		if (properties.isEmpty()) {
			setProperties(alias.properties);
		}
		if (javaVersion == null) {
			javaVersion(alias.javaVersion);
		}
		if (mainClass == null) {
			mainClass(alias.mainClass);
		}
	}

	public static boolean isAlias(ResourceRef resourceRef) {
		return resourceRef instanceof AliasResourceResolver.AliasedResourceRef;
	}
}
