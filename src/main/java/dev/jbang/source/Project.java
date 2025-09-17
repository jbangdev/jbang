package dev.jbang.source;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

/**
 * This class gives access to all information necessary to turn source files
 * into something that can be executed. Typically, this means that it holds
 * references to source files, resources and dependencies which can be used by
 * the <code>AppBuilder</code> to create a JAR file, for example.
 */
public class Project {
	@NonNull
	private final ResourceRef resourceRef;
	private Source mainSource;

	// Public (user) input values (can be changed from the outside at any time)
	private final SourceSet mainSourceSet = new SourceSet();
	private final List<MavenRepo> repositories = new ArrayList<>();
	// TODO move runtimeOptions to CmdGenerator!
	private final List<String> runtimeOptions = new ArrayList<>();
	private Map<String, String> properties = new HashMap<>();
	private final Map<String, String> manifestAttributes = new LinkedHashMap<>();
	private String javaVersion;
	private String description;
	private final List<DocRef> docs = new ArrayList<>();
	private String gav;
	private String mainClass;
	private String moduleName;
	private boolean nativeImage;
	private boolean integrations = true;
	private boolean enablePreviewRequested;

	private JdkManager jdkManager;

	private final List<Project> subProjects = new ArrayList<>();

	// Cached values
	private String stableId;
	private Jdk.InstalledJdk projectJdk;

	public static final String ATTR_PREMAIN_CLASS = "Premain-Class";
	public static final String ATTR_AGENT_CLASS = "Agent-Class";

	public enum BuildFile {
		jbang("build.jbang");

		public final String fileName;

		BuildFile(String fileName) {
			this.fileName = fileName;
		}

		public static List<String> fileNames() {
			return Arrays.stream(values()).map(v -> v.fileName).collect(Collectors.toList());
		}
	}

	public static ProjectBuilder builder() {
		return new ProjectBuilder();
	}

	public Project(@NonNull ResourceRef resourceRef) {
		this.resourceRef = resourceRef;
	}

	// TODO This should be refactored and removed
	public Project(@NonNull Source mainSource) {
		this.resourceRef = mainSource.getResourceRef();
		this.mainSource = mainSource;
	}

	@NonNull
	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	@NonNull
	public SourceSet getMainSourceSet() {
		return mainSourceSet;
	}

	@NonNull
	public List<MavenRepo> getRepositories() {
		return Collections.unmodifiableList(repositories);
	}

	@NonNull
	public Project addRepository(@NonNull MavenRepo repository) {
		repositories.add(repository);
		return this;
	}

	@NonNull
	public Project addRepositories(@NonNull Collection<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
		return this;
	}

	@NonNull
	public List<String> getRuntimeOptions() {
		return Collections.unmodifiableList(runtimeOptions);
	}

	@NonNull
	public Project addRuntimeOption(@NonNull String option) {
		runtimeOptions.add(option);
		return this;
	}

	@NonNull
	public Project addRuntimeOptions(@NonNull Collection<String> options) {
		runtimeOptions.addAll(options);
		return this;
	}

	@NonNull
	public List<Project> getSubProjects() {
		return Collections.unmodifiableList(subProjects);
	}

	public void addSubProject(@NonNull Project subProject) {
		subProjects.add(subProject);
	}

	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public void putProperties(@NonNull Map<String, String> properties) {
		this.properties = properties;
	}

	@NonNull
	public Map<String, String> getManifestAttributes() {
		return manifestAttributes;
	}

	public void setAgentMainClass(String agentMainClass) {
		manifestAttributes.put(ATTR_AGENT_CLASS, agentMainClass);
	}

	public void setPreMainClass(String preMainClass) {
		manifestAttributes.put(ATTR_PREMAIN_CLASS, preMainClass);
	}

	@Nullable
	public String getJavaVersion() {
		return javaVersion;
	}

	@NonNull
	public Project setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	@NonNull
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	@NonNull
	public Project setDescription(String description) {
		this.description = description;
		return this;
	}

	@NonNull
	public List<DocRef> getDocs() {
		return Collections.unmodifiableList(docs);
	}

	@NonNull
	public Project addDoc(DocRef docs) {
		this.docs.add(docs);
		return this;
	}

	@NonNull
	public Project addDocs(List<DocRef> docs) {
		this.docs.addAll(docs);
		return this;
	}

	@NonNull
	public Optional<String> getGav() {
		return Optional.ofNullable(gav);
	}

	@NonNull
	public Project setGav(String gav) {
		this.gav = gav;
		return this;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public boolean enablePreview() {
		return enablePreviewRequested || (mainSource != null && mainSource.enablePreview());
	}

	public void setEnablePreviewRequested(boolean enablePreview) {
		this.enablePreviewRequested = enablePreview;
	}

	@NonNull
	public Optional<String> getModuleName() {
		return Optional.ofNullable(moduleName);
	}

	@NonNull
	public Project setModuleName(String moduleName) {
		this.moduleName = moduleName;
		return this;
	}

	public boolean isNativeImage() {
		return nativeImage;
	}

	public void setNativeImage(boolean isNative) {
		this.nativeImage = isNative;
	}

	public boolean disableIntegrations() {
		return !integrations || (mainSource != null && mainSource.disableIntegrations());
	}

	public void setIntegrations(boolean integrations) {
		this.integrations = integrations;
	}

	public boolean enableCDS() {
		return mainSource != null && mainSource.enableCDS();
	}

	@Nullable
	public Source getMainSource() {
		return mainSource;
	}

	public void setMainSource(Source mainSource) {
		this.mainSource = mainSource;
	}

	public void setJdkManager(JdkManager jdkManager) {
		this.jdkManager = jdkManager;
	}

	protected String getStableId() {
		if (stableId == null) {
			Stream<String> sss = mainSourceSet.getStableIdInfo();
			if (moduleName != null) {
				Stream<String> s = Stream.of(ModuleUtil.getModuleName(this));
				sss = Stream.concat(sss, s);
			}
			stableId = Util.getStableID(sss);
		}
		return stableId;
	}

	protected void updateDependencyResolver(DependencyResolver resolver) {
		resolver.addRepositories(repositories);
		getMainSourceSet().updateDependencyResolver(resolver);
	}

	public JdkManager projectJdkManager() {
		if (jdkManager == null) {
			throw new IllegalStateException("No JdkManager set");
		}
		return jdkManager;
	}

	public Jdk.InstalledJdk projectJdk() {
		if (projectJdk == null) {
			projectJdk = projectJdkManager().getOrInstallJdk(getJavaVersion());
		}
		return projectJdk;
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
	 *
	 * @return A <code>Builder</code>
	 */
	@NonNull
	public Builder<CmdGeneratorBuilder> codeBuilder() {
		return CodeBuilderProvider.create(this).get();
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
	 *
	 * @param ctx will use the given <code>BuildContext</code> to store target files
	 *            and intermediate results
	 * @return A <code>Builder</code>
	 */
	@NonNull
	public static Builder<CmdGeneratorBuilder> codeBuilder(BuildContext ctx) {
		return CodeBuilderProvider.create(ctx).get();
	}

	public boolean isJar() {
		return Project.isJar(getResourceRef().getFile());
	}

	static boolean isJar(Path backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jar");
	}

	public boolean isJShell() {
		return Project.isJShell(getResourceRef().getFile());
	}

	static boolean isJShell(Path backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jsh");
	}
}
