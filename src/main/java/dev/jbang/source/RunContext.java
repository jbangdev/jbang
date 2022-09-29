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
 * This class contains all the extra information needed to actually run a
 * Source. These are either options given by the user on the command line or
 * things that are part of the user's environment. It's all the dynamic parts of
 * the execution where the Source is immutable. The RunContext and the Source
 * together determine what finally gets executed and how.
 */
public class RunContext {
	private List<String> additionalSources = Collections.emptyList();
	private List<String> additionalResources = Collections.emptyList();
	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalRepos = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private Map<String, String> properties = Collections.emptyMap();
	private Source.Type forceType = null;
	private String mainClass;
	private String javaAgentOption;
	private List<AgentSourceContext> javaAgents;
	private File catalogFile;

	private boolean alias;

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

	public static RunContext empty() {
		return new RunContext();
	}

	public List<String> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	public void setArguments(List<String> arguments) {
		if (arguments != null) {
			this.arguments = new ArrayList<>(arguments);
		} else {
			this.arguments = Collections.emptyList();
		}
	}

	public void setProperties(Map<String, String> properties) {
		if (properties != null) {
			this.properties = properties;
		} else {
			this.properties = Collections.emptyMap();
		}
	}

	public void setInteractive(boolean interactive) {
		this.interactive = interactive;
	}

	public void setEnableAssertions(boolean enableAssertions) {
		this.enableAssertions = enableAssertions;
	}

	public void setEnableSystemAssertions(boolean enableSystemAssertions) {
		this.enableSystemAssertions = enableSystemAssertions;
	}

	public void setFlightRecorderString(String flightRecorderString) {
		this.flightRecorderString = flightRecorderString;
	}

	public void setDebugString(String debugString) {
		this.debugString = debugString;
	}

	public void setClassDataSharing(Boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
	}

	public void setAdditionalSources(List<String> sources) {
		if (sources != null) {
			this.additionalSources = new ArrayList<>(sources);
		} else {
			this.additionalSources = Collections.emptyList();
		}
	}

	public void setAdditionalResources(List<String> resources) {
		if (resources != null) {
			this.additionalResources = new ArrayList<>(resources);
		} else {
			this.additionalResources = Collections.emptyList();
		}
	}

	public void setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
		mcp = null;
	}

	public void setAdditionalRepositories(List<String> repos) {
		if (repos != null) {
			this.additionalRepos = repos;
		} else {
			this.additionalRepos = Collections.emptyList();
		}
		mcp = null;
	}

	public void setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
		mcp = null;
	}

	/**
	 * Returns true if originalRef is an alias, otherwise false
	 */
	public boolean isAlias() {
		return alias;
	}

	/**
	 * Sets if originalRef is an alias or not
	 */
	public void setAlias(boolean alias) {
		this.alias = alias;
	}

	public void setForceType(Source.Type forceType) {
		this.forceType = forceType;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public void setJavaOptions(List<String> javaOptions) {
		if (javaOptions != null) {
			this.javaOptions = javaOptions;
		} else {
			this.javaOptions = Collections.emptyList();
		}
	}

	public String getJavaAgentOption() {
		return javaAgentOption;
	}

	public void setJavaAgentOption(String option) {
		this.javaAgentOption = option;
	}

	public void setNativeImage(boolean nativeImage) {
		this.nativeImage = nativeImage;
	}

	public void setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	public void setCatalog(File catalogFile) {
		this.catalogFile = catalogFile;
	}

	public void setBuildDir(Path buildDir) {
		this.buildDir = buildDir;
	}

	private Properties getContextProperties() {
		if (contextProperties == null) {
			contextProperties = new Properties(System.getProperties());
			// early/eager init to property resolution will work.
			new Detector().detect(contextProperties, Collections.emptyList());

			contextProperties.putAll(properties);
		}
		return contextProperties;
	}

	public static class AgentSourceContext {
		final public Project project;
		final public String javaAgentOption;

		private AgentSourceContext(Project prj, RunContext context) {
			this.project = prj;
			this.javaAgentOption = context.getJavaAgentOption();
		}
	}

	public void addJavaAgent(Project prj, RunContext ctx) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(new AgentSourceContext(prj, ctx));
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

	public Project forResource(String resource) {
		ResourceRef resourceRef = resolveChecked(getResourceResolver(), resource);
		return forResourceRef(resourceRef);
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
	public Project forFile(Path resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return forResourceRef(resourceRef);
	}

	public Project forResourceRef(ResourceRef resourceRef) {
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

	public Source createSource(ResourceRef resourceRef) {
		return Source.forResourceRef(resourceRef, forceType,
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
										.properties(properties)
										.javaAgents(javaAgents)
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
										.properties(properties)
										.sourceType(forceType)
										.javaAgents(javaAgents)
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
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		sources	.stream()
				.flatMap(f -> Util.explode(null, Util.getCwd(), f).stream())
				.map(s -> resolveChecked(resolver, s))
				.map(ref -> Source.forResourceRef(ref, forceType, propsResolver))
				.forEach(src -> src.updateProject(prj, resolver));
	}

	private List<RefTarget> allToFileRef(List<String> resources) {
		ResourceResolver resolver = ResourceResolver.forResources();
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		return resources.stream()
						.flatMap(f -> Source.explodeFileRef(null, Util.getCwd(), f).stream())
						.map(f -> Source.toFileRef(f, resolver))
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
				new RenamingScriptResourceResolver(),
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
			setJavaOptions(alias.javaOptions);
		}
		if (additionalSources.isEmpty()) {
			setAdditionalSources(alias.sources);
		}
		if (additionalResources.isEmpty()) {
			setAdditionalResources(alias.resources);
		}
		if (additionalDeps.isEmpty()) {
			setAdditionalDependencies(alias.dependencies);
		}
		if (additionalRepos.isEmpty()) {
			setAdditionalRepositories(alias.repositories);
		}
		if (additionalClasspaths.isEmpty()) {
			setAdditionalClasspaths(alias.classpaths);
		}
		if (properties.isEmpty()) {
			setProperties(alias.properties);
		}
		if (javaVersion == null) {
			setJavaVersion(alias.javaVersion);
		}
		if (mainClass == null) {
			setMainClass(alias.mainClass);
		}
		setAlias(true);
	}
}
