package dev.jbang.source;

import static dev.jbang.util.Util.MAIN_JAVA;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jspecify.annotations.NonNull;

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
import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.resources.resolvers.ClasspathResourceResolver;
import dev.jbang.resources.resolvers.FileResourceResolver;
import dev.jbang.resources.resolvers.GavResourceResolver;
import dev.jbang.resources.resolvers.LiteralScriptResourceResolver;
import dev.jbang.resources.resolvers.RemoteResourceResolver;
import dev.jbang.resources.resolvers.RenamingScriptResourceResolver;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.parser.Directives;
import dev.jbang.source.parser.KeyValue;
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
	private List<String> docs = new ArrayList<>();
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

	public ProjectBuilder docs(List<String> docs) {
		if (docs != null) {
			this.docs = docs;
		} else {
			this.docs = Collections.emptyList();
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
			.map(propertyReplacer())
			.collect(Collectors.toList());
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
		Directives directives = new Directives.JbangProject(contents, propertyReplacer());
		ResourceResolver sibRes1 = getSiblingResolver(resourceRef);
		prj.setDescription(directives.description());
		prj.addDocs(allToDocRef(directives.collectDocs(), sibRes1));
		prj.setGav(directives.gav());
		prj.setMainClass(directives.mainMethod());
		prj.setModuleName(directives.module());

		SourceSet ss = prj.getMainSourceSet();
		ss.addResources(allToFileRef(directives.files(), resourceRef, sibRes1));
		ss.addDependencies(directives.binaryDependencies());
		ss.addCompileOptions(directives.compileOptions());
		ss.addNativeOptions(directives.nativeOptions());
		prj.addRepositories(directives.repositories());
		prj.addRuntimeOptions(directives.runtimeOptions());
		directives.manifestOptions().forEach(kv -> {
			if (!kv.getKey().isEmpty()) {
				prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
			}
		});
		directives.agentOptions().forEach(kv -> {
			if (!kv.getKey().isEmpty()) {
				prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
			}
		});
		String version = directives.javaVersion();
		if (version != null && JavaUtil.checkRequestedVersion(version)) {
			if (new JavaUtil.RequestedVersionComparator().compare(prj.getJavaVersion(), version) > 0) {
				prj.setJavaVersion(version);
			}
		}

		ResourceResolver resolver = getAliasResourceResolver(null);
		ResourceResolver sibRes2 = getSiblingResolver(resourceRef, resolver);
		for (String srcDep : directives.sourceDependencies()) {
			ResourceRef subRef = resolver.resolve(srcDep, true);
			prj.addSubProject(new ProjectBuilder(buildRefs).build(subRef));
		}

		boolean first = true;
		List<String> sources = directives.sources();
		if (sources.isEmpty()) {
			// if no sources are defined, we go look for a couple of possible options
			if (resourceRef.resolve(MAIN_JAVA) != null) {
				sources.add(MAIN_JAVA);
			} else {
				if (resourceRef.resolve("src/main/java") != null) {
					sources.add("src/main/java/**.java");
				}
				if (resourceRef.resolve("src/main/kotlin") != null) {
					sources.add("src/main/kotlin/**.kt");
				}
				if (resourceRef.resolve("src/main/groovy") != null) {
					sources.add("src/main/groovy/**.groovy");
				}
			}
		}
		List<Source> includedSources = allToSource(sources, resourceRef, sibRes2);
		for (Source includedSource : includedSources) {
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
				propertyReplacer());

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
		ss.addResources(
				allToFileRef(allToKV(replaceAllProps(additionalResources)), null, ResourceResolver.forResources()));
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
		prj.addDocs(allToDocRef(allToKV(docs), ResourceResolver.forResources()));
		if (jdkManager != null) {
			prj.setJdkManager(jdkManager);
		} else {
			prj.setJdkManager(JavaUtil.defaultJdkManager());
		}
		return prj;
	}

	private void updateAllSources(Project prj, List<String> sources) {
		ResourceResolver resolver = getResourceResolver();
		sources.stream()
			.flatMap(f -> Util.explode(null, Util.getCwd(), f).stream())
			.map(s -> resolveChecked(resolver, s))
			.map(this::createSource)
			.forEach(src -> updateProject(src, prj, resolver));
	}

	private List<KeyValue> allToKV(List<String> list) {
		return list.stream().map(KeyValue::of).collect(Collectors.toList());
	}

	private List<MavenRepo> allToMavenRepo(List<String> repos) {
		return repos.stream().map(DependencyUtil::toMavenRepo).collect(Collectors.toList());
	}

	private List<Source> allToSource(List<String> sources, ResourceRef resourceRef, ResourceResolver resolver) {
		String org = resourceRef != null ? resourceRef.getOriginalResource() : null;
		Path baseDir = org != null ? resourceRef.getFile().toAbsolutePath().getParent() : Util.getCwd();
		return sources.stream()
			.flatMap(line -> Util.explode(org, baseDir, line).stream())
			.map(ref -> Source.forResource(resolver, ref, propertyReplacer()))
			.collect(Collectors.toList());
	}

	private List<RefTarget> allToFileRef(List<KeyValue> resources, ResourceRef ref, ResourceResolver resolver) {
		String org = ref != null ? ref.getOriginalResource() : null;
		Path baseDir = org != null ? ref.getFile().toAbsolutePath().getParent() : Util.getCwd();
		return resources.stream()
			.flatMap(kv -> Directives.explodeFileRef(org, baseDir, kv).stream())
			.map(f -> toFileRef(f, resolver))
			.collect(Collectors.toList());
	}

	private List<DocRef> allToDocRef(List<KeyValue> docs, ResourceResolver resolver) {
		return docs.stream()
			.map(kv -> DocRef.toDocRef(resolver, kv))
			.collect(Collectors.toList());
	}

	/**
	 * Turns a reference like `img/logo.jpg` or `WEB-INF/index.html=web/index.html`
	 * into a <code>RefTarget</code>. In case no alias was supplied the
	 * <code>RefTarget</code>'s target will be <code>null</code>. When an alias
	 * terminates in a <code>/</code> the alias will be assumed to be a folder name
	 * and the source's file name will be appended to it, meaning that given
	 * `WEB-INF/=web/index.html` this method will return a <code>RefTarget</code> as
	 * if `WEB-INF/index.html=web/index.html` was supplied.
	 */
	public static RefTarget toFileRef(String fileReference, ResourceResolver siblingResolver) {
		String[] split = fileReference.split("=", 2);
		String src;
		String dest = null;

		if (split.length == 1) {
			src = split[0];
		} else {
			dest = split[0];
			src = split[1];
		}

		Path p = dest != null ? Paths.get(dest) : null;

		if (Paths.get(src).isAbsolute() || (p != null && p.isAbsolute())) {
			ResourceRef ref = ResourceRef.forUnresolvable(src,
					"Only relative paths allowed in //FILES. Found absolute path");
			return RefTarget.create(ref, p);
		}

		try {
			ResourceRef ref = siblingResolver.resolve(src);
			if (ref == null) {
				ref = ResourceRef.forUnresolvable(src, "not resolvable from " + siblingResolver.description());
			}
			if (dest != null && dest.endsWith("/")) {
				p = p.resolve(ref.getFile().getFileName());
			}
			return RefTarget.create(ref, p);
		} catch (ResourceNotFoundException rnfe) {
			ResourceRef ref = ResourceRef.forUnresolvable(src,
					"error `" + rnfe.getMessage() + "' while resolving from " + siblingResolver.description());
			return RefTarget.create(ref, p);
		}
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
		prj.setDescription(src.getDirectives().description());
		prj.setGav(src.getDirectives().gav());
		prj.setMainClass(src.getDirectives().mainMethod());
		prj.setModuleName(src.getDirectives().module());
		if (prj.getMainSource() instanceof JavaSource) {
			// todo: have way to turn these off? lets wait until someone asks and has a
			// usecase where ability to debug and support named parameters is bad.
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
	@NonNull
	private Project updateProject(Source src, Project prj, ResourceResolver resolver) {
		ResourceRef srcRef = src.getResourceRef();
		if (!prj.getMainSourceSet().getSources().contains(srcRef)) {
			SourceSet ss = prj.getMainSourceSet();
			ss.addSource(srcRef);
			if (srcRef instanceof ResourceRef.UnresolvableResourceRef) {
				Util.verboseMsg("Skipping unresolvable source: " + srcRef);
				return prj;
			}
			ResourceResolver sibRes1 = getSiblingResolver(srcRef);
			ss.addResources(allToFileRef(src.getDirectives().files(), srcRef, sibRes1));
			ss.addDependencies(src.collectBinaryDependencies());
			ss.addCompileOptions(src.getCompileOptions());
			ss.addNativeOptions(src.getNativeOptions());
			prj.addRepositories(src.getDirectives().repositories());
			prj.addRuntimeOptions(src.getRuntimeOptions());
			prj.addDocs(allToDocRef(src.getDirectives().collectDocs(), sibRes1));

			src.getDirectives().manifestOptions().forEach(kv -> {
				if (!kv.getKey().isEmpty()) {
					prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
				}
			});
			src.getDirectives().agentOptions().forEach(kv -> {
				if (!kv.getKey().isEmpty()) {
					prj.getManifestAttributes().put(kv.getKey(), kv.getValue() != null ? kv.getValue() : "true");
				}
			});
			String version = src.getDirectives().javaVersion();
			if (version != null && JavaUtil.checkRequestedVersion(version)) {
				if (new JavaUtil.RequestedVersionComparator().compare(prj.getJavaVersion(), version) > 0) {
					prj.setJavaVersion(version);
				}
			}
			for (String srcDep : src.collectSourceDependencies()) {
				ResourceRef subRef = sibRes1.resolve(srcDep, true);
				prj.addSubProject(new ProjectBuilder(buildRefs).build(subRef));
			}
			ResourceResolver sibRes2 = getSiblingResolver(srcRef, resolver);
			List<Source> includedSources = allToSource(src.getDirectives().sources(), srcRef, sibRes2);
			for (Source includedSource : includedSources) {
				updateProject(includedSource, prj, resolver);
			}
		}
		return prj;
	}

	private ResourceResolver getResourceResolver() {
		Catalog cat = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		return new AliasResourceResolver(cat, this::getAliasResourceResolver);
	}

	private static ResourceResolver getSiblingResolver(ResourceRef ref) {
		return getSiblingResolver(ref, ResourceResolver.forResources());
	}

	private static ResourceResolver getSiblingResolver(ResourceRef ref, ResourceResolver resolver) {
		return ResourceResolver.combined(ref, ResourceResolver.trusting(resolver));
	}

	private ResourceResolver getAliasResourceResolver(Alias alias) {
		if (alias != null) {
			updateFromAlias(alias);
		}
		return ResourceResolver.combined(
				new RenamingScriptResourceResolver(forceType),
				new LiteralScriptResourceResolver(forceType),
				new GavResourceResolver(this::resolveDependency),
				new RemoteResourceResolver(false),
				new ClasspathResourceResolver(),
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
		if (forceType == null && alias.forceType != null) {
			forceType(Source.Type.valueOf(alias.forceType));
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
		if (docs == null) {
			docs(alias.docs);
		}
	}

	public static boolean isAlias(ResourceRef resourceRef) {
		return resourceRef instanceof AliasResourceResolver.AliasedResourceRef;
	}

	private Function<String, String> propertyReplacer() {
		return item -> PropertiesValueResolver.replaceProperties(item, getContextProperties());
	}

}
