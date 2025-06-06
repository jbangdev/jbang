package dev.jbang.source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import dev.jbang.Settings;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.Detector;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.resolvers.AliasResourceResolver;
import dev.jbang.source.resolvers.ClasspathResourceResolver;
import dev.jbang.source.resolvers.CombinedResourceResolver;
import dev.jbang.source.resolvers.FileResourceResolver;
import dev.jbang.source.resolvers.GavResourceResolver;
import dev.jbang.source.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.resolvers.RemoteResourceResolver;
import dev.jbang.source.resolvers.RenamingScriptResourceResolver;
import dev.jbang.source.resolvers.SiblingResourceResolver;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

/**
 * This class constructs a <code>Project</code>. It uses the options given by
 * the user on the command line or things that are part of the user's
 * environment.
 */
public class ProjectBuilder {
	private List<String> additionalSources = new ArrayList<>();
	private List<String> additionalResources = new ArrayList<>();
	private List<String> additionalDeps = new ArrayList<>();
	private List<String> additionalRepos = new ArrayList<>();
	private List<String> additionalClasspaths = new ArrayList<>();
	private Map<String, String> properties = new HashMap<>();
	private Source.Type forceType = null;
	private String mainClass;
	private String moduleName;
	private List<String> compileOptions = Collections.emptyList();
	private List<String> nativeOptions = Collections.emptyList();
	private Map<String, String> manifestOptions = new HashMap<>();
	private File catalogFile;
	private Boolean nativeImage;
	private Boolean integrations;
	private String javaVersion;
	private Boolean enablePreview;
	private JdkManager jdkManager;

	// Cached values
	private Properties contextProperties;
	private ModularClassPath mcp;
	private final Set<ResourceRef> buildRefs;

	ProjectBuilder() {
		buildRefs = new HashSet<>();
	}

	private ProjectBuilder(Set<ResourceRef> buildRefs) {
		this.buildRefs = buildRefs;
	}

	public ProjectBuilder setProperties(Map<String, String> properties) {
		if (properties != null) {
			this.properties = properties;
		} else {
			this.properties = Collections.emptyMap();
		}
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

	public ProjectBuilder moduleName(String moduleName) {
		this.moduleName = moduleName;
		return this;
	}

	public ProjectBuilder compileOptions(List<String> compileOptions) {
		if (compileOptions != null) {
			this.compileOptions = compileOptions;
		} else {
			this.compileOptions = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder nativeOptions(List<String> nativeOptions) {
		if (nativeOptions != null) {
			this.nativeOptions = nativeOptions;
		} else {
			this.nativeOptions = Collections.emptyList();
		}
		return this;
	}

	public ProjectBuilder manifestOptions(Map<String, String> manifestOptions) {
		if (manifestOptions != null) {
			this.manifestOptions = manifestOptions;
		} else {
			this.manifestOptions = Collections.emptyMap();
		}
		return this;
	}

	public ProjectBuilder nativeImage(Boolean nativeImage) {
		this.nativeImage = nativeImage;
		return this;
	}

	public ProjectBuilder integrations(Boolean integrations) {
		this.integrations = integrations;
		return this;
	}

	public ProjectBuilder enablePreview(Boolean enablePreviewRequested) {
		this.enablePreview = enablePreviewRequested;
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

	public ProjectBuilder jdkManager(JdkManager jdkManager) {
		this.jdkManager = jdkManager;
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

	private ResourceRef resolveChecked(ResourceResolver resolver, String resource) {
		Util.verboseMsg("Resolving resource ref: " + resource);
		boolean retryCandidate = catalogFile == null && !Util.isFresh() && Settings.getCacheEvict() > 0
				&& (Catalog.isValidName(resource) || Catalog.isValidCatalogReference(resource)
						|| Util.isRemoteRef(resource));
		ResourceRef ref = null;
		try {
			ref = resolver.resolve(resource);
		} catch (ExitException ee) {
			if (ee.getStatus() != BaseCommand.EXIT_INVALID_INPUT || !retryCandidate) {
				throw ee;
			}
		}
		if (ref == null && retryCandidate) {
			// We didn't get a result and the resource looks like something
			// that could be an alias or a remote URL, so we'll try again
			// with the cache evict set to 0, forcing Jbang to actually check
			// if all its cached information is up-to-date.
			Util.verboseMsg("Retry using cache-evict: " + resource);
			ref = Util.withCacheEvict(() -> resolver.resolve(resource));
		}
		if (ref == null || !Files.isReadable(ref.getFile())) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Script or alias could not be found or read: '" + resource + "'");
		}
		Util.verboseMsg("Resolved resource ref as: " + ref);
		return ref;
	}

	// Only used by tests
	public Project build(Path resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return build(resourceRef);
	}

	public Project build(ResourceRef resourceRef) {
		if (!buildRefs.add(resourceRef)) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Self-referencing project dependency found for: '" + resourceRef.getOriginalResource() + "'");
		}

		Project prj;
		if (resourceRef.getFile().getFileName().toString().endsWith(".jar")) {
			prj = createJarProject(resourceRef);
		} else if (Util.isPreview()
				&& resourceRef.getFile().getFileName().toString().equals(Project.BuildFile.jbang.fileName)) {
			prj = createJbangProject(resourceRef);
		} else {
			prj = createSourceProject(resourceRef);
		}
		return prj;
	}

	private Project createJarProject(ResourceRef resourceRef) {
		Project prj = new Project(resourceRef);
		if (resourceRef.getOriginalResource() != null
				&& DependencyUtil.looksLikeAGav(resourceRef.getOriginalResource())) {
			prj.getMainSourceSet().addDependency(resourceRef.getOriginalResource());
		}
		return updateProject(importJarMetadata(prj, moduleName != null && moduleName.isEmpty()));
	}

	private Project createJbangProject(ResourceRef resourceRef) {
		Project prj = new Project(resourceRef);
		String contents = Util.readFileContent(resourceRef.getFile());
		TagReader tagReader = new TagReader.JbangProject(contents,
				it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));
		prj.setDescription(tagReader.getDescription().orElse(null));
		prj.setGav(tagReader.getGav().orElse(null));
		prj.setMainClass(tagReader.getMain().orElse(null));
		prj.setModuleName(tagReader.getModule().orElse(null));

		SourceSet ss = prj.getMainSourceSet();
		ss.addResources(tagReader.collectFiles(resourceRef,
				new SiblingResourceResolver(resourceRef, ResourceResolver.forResources())));
		ss.addDependencies(tagReader.collectBinaryDependencies());
		ss.addCompileOptions(tagReader.collectOptions("JAVAC_OPTIONS", "COMPILE_OPTIONS"));
		ss.addNativeOptions(tagReader.collectOptions("NATIVE_OPTIONS"));
		prj.addRepositories(tagReader.collectRepositories());
		prj.addRuntimeOptions(tagReader.collectOptions("JAVA_OPTIONS", "RUNTIME_OPTIONS"));
		tagReader.collectManifestOptions().forEach(kv -> {
			if (!kv.getKey().isEmpty()) {
				prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
			}
		});
		tagReader.collectAgentOptions().forEach(kv -> {
			if (!kv.getKey().isEmpty()) {
				prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
			}
		});
		String version = tagReader.getJavaVersion();
		if (version != null && JavaUtil.checkRequestedVersion(version)) {
			if (new JavaUtil.RequestedVersionComparator().compare(prj.getJavaVersion(), version) > 0) {
				prj.setJavaVersion(version);
			}
		}

		ResourceResolver resolver = getAliasResourceResolver(null);
		ResourceResolver siblingResolver = new SiblingResourceResolver(resourceRef, resolver);

		for (String srcDep : tagReader.collectSourceDependencies()) {
			ResourceRef subRef = resolver.resolve(srcDep, true);
			prj.addSubProject(new ProjectBuilder(buildRefs).build(subRef));
		}

		boolean first = true;
		for (Source includedSource : tagReader.collectSources(resourceRef, siblingResolver)) {
			updateProject(includedSource, prj, resolver);
			if (first) {
				prj.setMainSource(includedSource);
				first = false;
			}
		}

		return updateProject(prj);
	}

	private Project createSourceProject(ResourceRef resourceRef) {
		Source src = createSource(resourceRef);
		Project prj = new Project(src);
		return updateProject(updateProjectMain(src, prj, getResourceResolver()));
	}

	private Source createSource(ResourceRef resourceRef) {
		return Source.forResourceRef(resourceRef,
				it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));

	}

	public Project build(Source src) {
		Project prj = new Project(src);
		return updateProject(updateProjectMain(src, prj, getResourceResolver()));
	}

	/*
	 * Imports settings from jar MANIFEST.MF, pom.xml and more
	 */
	private Project importJarMetadata(Project prj, boolean importModuleName) {
		Path jar = prj.getResourceRef().getFile();
		if (jar != null && Files.exists(jar)) {
			try (JarFile jf = new JarFile(jar.toFile())) {
				String moduleName = ModuleUtil.getModuleName(jar);
				if (moduleName != null && importModuleName) {
					// We only import the module name if the project's module
					// name was set to an empty string, which basically means
					// "we want module support, but we don't know the name".
					prj.setModuleName(moduleName);
				}

				if (jf.getManifest() != null) {
					Attributes attrs = jf.getManifest().getMainAttributes();
					if (attrs.containsKey(Attributes.Name.MAIN_CLASS)) {
						prj.setMainClass(attrs.getValue(Attributes.Name.MAIN_CLASS));
					}
					String ver = attrs.getValue(JarBuildStep.ATTR_BUILD_JDK);
					if (ver != null) {
						// buildJdk = JavaUtil.parseJavaVersion(ver);
						prj.setJavaVersion(JavaUtil.parseJavaVersion(ver) + "+");
					}

					// we pass exports/opens into the project...
					// TODO: this does mean we can't separate from user specified options and jar
					// origined ones, but not sure if needed?
					// https://openjdk.org/jeps/261#Breaking-encapsulation
					String exports = attrs.getValue("Add-Exports");
					if (exports != null) {
						prj.getManifestAttributes().put("Add-Exports", exports);
					}
					String opens = attrs.getValue("Add-Opens");
					if (opens != null) {
						prj.getManifestAttributes().put("Add-Opens", exports);
					}

				}

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

			} catch (IOException e) {
				Util.warnMsg("Problem reading manifest from " + jar);
			}
		}
		return prj;
	}

	private Project updateProject(Project prj) {
		SourceSet ss = prj.getMainSourceSet();
		prj.addRepositories(allToMavenRepo(replaceAllProps(additionalRepos)));
		ss.addDependencies(replaceAllProps(additionalDeps));
		ss.addClassPaths(replaceAllProps(additionalClasspaths));
		updateAllSources(prj, replaceAllProps(additionalSources));
		ss.addResources(allToFileRef(replaceAllProps(additionalResources)));
		ss.addCompileOptions(compileOptions);
		ss.addNativeOptions(nativeOptions);
		prj.putProperties(properties);
		prj.getManifestAttributes().putAll(manifestOptions);
		if (moduleName != null) {
			if (!moduleName.isEmpty() || !prj.getModuleName().isPresent()) {
				prj.setModuleName(moduleName);
			}
		}
		if (mainClass != null) {
			prj.setMainClass(mainClass);
		}
		if (javaVersion != null) {
			prj.setJavaVersion(javaVersion);
		}
		if (nativeImage != null) {
			prj.setNativeImage(nativeImage);
		}
		if (integrations != null) {
			prj.setIntegrations(integrations);
		}
		if (enablePreview != null) {
			prj.setEnablePreviewRequested(enablePreview);
		}
		if (jdkManager != null) {
			prj.setJdkManager(jdkManager);
		} else {
			prj.setJdkManager(JavaUtil.defaultJdkManager());
		}
		return prj;
	}

	private void updateAllSources(Project prj, List<String> sources) {
		Catalog catalog = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		ResourceResolver resolver = getResourceResolver();
		sources.stream()
			.flatMap(f -> Util.explode(null, Util.getCwd(), f).stream())
			.map(s -> resolveChecked(resolver, s))
			.map(this::createSource)
			.forEach(src -> updateProject(src, prj, resolver));
	}

	private List<RefTarget> allToFileRef(List<String> resources) {
		ResourceResolver resolver = ResourceResolver.forResources();
		return resources.stream()
			.flatMap(f -> TagReader.explodeFileRef(null, Util.getCwd(), f).stream())
			.map(f -> TagReader.toFileRef(f, resolver))
			.collect(Collectors.toList());
	}

	/**
	 * Updates the given <code>Project</code> with all the information from the
	 * <code>Source</code> when that source is the main file. It updates certain
	 * things at the project level and then calls <code>updateProject()</code> which
	 * will update things at the <code>SourceSet</code> level.
	 *
	 * @param prj      The <code>Project</code> to update
	 * @param resolver The resolver to use for dependent (re)sources
	 * @return A <code>Project</code>
	 */
	private Project updateProjectMain(Source src, Project prj, ResourceResolver resolver) {
		prj.setDescription(src.tagReader.getDescription().orElse(null));
		prj.setGav(src.tagReader.getGav().orElse(null));
		prj.setMainClass(src.tagReader.getMain().orElse(null));
		prj.setModuleName(src.tagReader.getModule().orElse(null));
		if (prj.getMainSource() instanceof JavaSource) {
			// todo: have way to turn these off? lets wait until someone asks and has a
			// usecase
			// where ability to debug and support named parameters is bad.
			prj.getMainSourceSet().addCompileOption("-g");
			prj.getMainSourceSet().addCompileOption("-parameters");
		}
		return updateProject(src, prj, resolver);
	}

	/**
	 * Updates the given <code>Project</code> with all the information from the
	 * <code>Source</code>. This includes the current source file with all other
	 * source files it references, all resource files, anything to do with
	 * dependencies, repositories and class paths as well as compile time and
	 * runtime options.
	 *
	 * @param prj      The <code>Project</code> to update
	 * @param resolver The resolver to use for dependent (re)sources
	 * @return The given <code>Project</code>
	 */
	@Nonnull
	private Project updateProject(Source src, Project prj, ResourceResolver resolver) {
		ResourceRef srcRef = src.getResourceRef();
		if (!prj.getMainSourceSet().getSources().contains(srcRef)) {
			ResourceResolver sibRes1 = new SiblingResourceResolver(srcRef, ResourceResolver.forResources());
			SourceSet ss = prj.getMainSourceSet();
			ss.addSource(srcRef);
			ss.addResources(src.tagReader.collectFiles(srcRef, sibRes1));
			ss.addDependencies(src.collectBinaryDependencies());
			ss.addCompileOptions(src.getCompileOptions());
			ss.addNativeOptions(src.getNativeOptions());
			prj.addRepositories(src.tagReader.collectRepositories());
			prj.addRuntimeOptions(src.getRuntimeOptions());
			src.tagReader.collectManifestOptions().forEach(kv -> {
				if (!kv.getKey().isEmpty()) {
					prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
				}
			});
			src.tagReader.collectAgentOptions().forEach(kv -> {
				if (!kv.getKey().isEmpty()) {
					prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
				}
			});
			String version = src.tagReader.getJavaVersion();
			if (version != null && JavaUtil.checkRequestedVersion(version)) {
				if (new JavaUtil.RequestedVersionComparator().compare(prj.getJavaVersion(), version) > 0) {
					prj.setJavaVersion(version);
				}
			}
			for (String srcDep : src.collectSourceDependencies()) {
				ResourceRef subRef = sibRes1.resolve(srcDep, true);
				prj.addSubProject(new ProjectBuilder(buildRefs).build(subRef));
			}
			ResourceResolver sibRes2 = new SiblingResourceResolver(srcRef, resolver);
			for (Source includedSource : src.tagReader.collectSources(srcRef, sibRes2)) {
				updateProject(includedSource, prj, resolver);
			}
		}
		return prj;
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
				new LiteralScriptResourceResolver(forceType),
				new RemoteResourceResolver(false),
				new ClasspathResourceResolver(),
				new GavResourceResolver(this::resolveDependency),
				new FileResourceResolver());
	}

	private ModularClassPath resolveDependency(String dep) {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver()
				.addDependency(dep)
				.addRepositories(allToMavenRepo(
						replaceAllProps(additionalRepos)))
				.addDependencies(replaceAllProps(additionalDeps))
				.addClassPaths(
						replaceAllProps(additionalClasspaths));
			mcp = resolver.resolve();
		}
		return mcp;
	}

	private void updateFromAlias(Alias alias) {
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
		if (compileOptions.isEmpty()) {
			compileOptions(alias.compileOptions);
		}
		if (nativeImage == null) {
			nativeImage(alias.nativeImage);
		}
		if (nativeOptions.isEmpty()) {
			nativeOptions(alias.nativeOptions);
		}
		if (integrations == null) {
			integrations(alias.integrations);
		}
		if (manifestOptions.isEmpty()) {
			manifestOptions(alias.manifestOptions);
		}
		if (enablePreview == null) {
			enablePreview(alias.enablePreview);
		}
	}

	public static boolean isAlias(ResourceRef resourceRef) {
		return resourceRef instanceof AliasResourceResolver.AliasedResourceRef;
	}

}
