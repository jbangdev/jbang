package dev.jbang.source;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.resolvers.SiblingResourceResolver;
import dev.jbang.source.sources.*;
import dev.jbang.source.sources.KotlinSource;
import dev.jbang.source.sources.MarkdownSource;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

/**
 * A Script represents a Source (something runnable) in the form of a source
 * file. It's code that first needs to be compiled before it can be executed.
 * The Script extracts as much information from the source file as it can, like
 * all `//`-directives (eg. `//SOURCES`, `//DEPS`, etc.)
 *
 * NB: The Script contains/returns no other information than that which can be
 * induced from the source file. So all Scripts that refer to the same source
 * file will contain/return the exact same information.
 */
public abstract class Source {

	private final ResourceRef resourceRef;
	private final String contents;
	protected final TagReader tagReader;

	public enum Type {
		java("java"), jshell("jsh"), kotlin("kt"),
		groovy("groovy"), markdown("md");

		public final String extension;

		Type(String extension) {
			this.extension = extension;
		}

		public static List<String> extensions() {
			return Arrays.stream(values()).map(v -> v.extension).collect(Collectors.toList());
		}
	}

	public Source(String contents, Function<String, String> replaceProperties) {
		this(ResourceRef.nullRef, contents, replaceProperties);
	}

	protected Source(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		this(resourceRef, Util.readFileContent(resourceRef.getFile()), replaceProperties);
	}

	protected Source(ResourceRef resourceRef, String contents, Function<String, String> replaceProperties) {
		this.resourceRef = resourceRef;
		this.contents = contents;
		this.tagReader = createTagReader(contents, replaceProperties);
	}

	protected TagReader createTagReader(String contents, Function<String, String> replaceProperties) {
		return new TagReader.Extended(contents, replaceProperties);
	}

	@Nonnull
	public Stream<String> getTags() {
		return tagReader.getTags();
	}

	protected List<String> collectDependencies() {
		return tagReader.collectDependencies();
	}

	protected abstract List<String> getCompileOptions();

	protected abstract List<String> getNativeOptions();

	protected abstract List<String> getRuntimeOptions();

	public abstract Builder<CmdGeneratorBuilder> getBuilder(Project prj, BuildContext ctx);

	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	@Nonnull
	public Optional<String> getJavaPackage() {
		if (contents != null) {
			return Util.getSourcePackage(contents);
		} else {
			return Optional.empty();
		}
	}

	public boolean isAgent() {
		return !tagReader.collectAgentOptions().isEmpty();
	}

	public boolean enableCDS() {
		return !tagReader.collectRawOptions("CDS").isEmpty();
	}

	/**
	 * Updates the given <code>Project</code> with all the information from this
	 * <code>Source</code> when that source is the main file. It updates certain
	 * things at the project level and then calls <code>updateProject()</code> which
	 * will update things at the <code>SourceSet</code> level.
	 *
	 * @param prj      The <code>Project</code> to update
	 * @param resolver The resolver to use for dependent (re)sources
	 * @return A <code>Project</code>
	 */
	public Project updateProjectMain(Project prj, ResourceResolver resolver) {
		prj.setDescription(tagReader.getDescription().orElse(null));
		prj.setGav(tagReader.getGav().orElse(null));
		prj.setMainClass(tagReader.getMain().orElse(null));
		prj.setModuleName(tagReader.getModule().orElse(null));
		return updateProject(prj, resolver);
	}

	/**
	 * Updates the given <code>Project</code> with all the information from this
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
	public Project updateProject(Project prj, ResourceResolver resolver) {
		if (!prj.getMainSourceSet().getSources().contains(getResourceRef())) {
			SourceSet ss = prj.getMainSourceSet();
			ss.addSource(this.getResourceRef());
			ss.addResources(tagReader.collectFiles(resourceRef,
					new SiblingResourceResolver(resourceRef, ResourceResolver.forResources())));
			ss.addDependencies(collectDependencies());
			ss.addCompileOptions(getCompileOptions());
			ss.addNativeOptions(getNativeOptions());
			prj.addRepositories(tagReader.collectRepositories());
			prj.addRuntimeOptions(getRuntimeOptions());
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
			ResourceResolver siblingResolver = new SiblingResourceResolver(getResourceRef(), resolver);
			for (Source includedSource : tagReader.collectSources(resourceRef, siblingResolver)) {
				includedSource.updateProject(prj, resolver);
			}
		}
		return prj;
	}

	// Used only by tests
	static Source forResource(String resource, Function<String, String> replaceProperties) {
		return forResource(ResourceResolver.forResources(), resource, replaceProperties);
	}

	static Source forResource(ResourceResolver resolver, String resource,
			Function<String, String> replaceProperties) {
		ResourceRef resourceRef = resolver.resolve(resource);
		if (resourceRef == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not find: " + resource);
		}
		return forResourceRef(resourceRef, replaceProperties);
	}

	public static Source forResourceRef(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		String ext = resourceRef.getExtension();
		if (ext.equals("kt")) {
			return new KotlinSource(resourceRef, replaceProperties);
		} else if (ext.equals("groovy")) {
			return new GroovySource(resourceRef, replaceProperties);
		} else if (ext.equals("jsh")) {
			return new JshSource(resourceRef, replaceProperties);
		} else if (ext.equals("md")) {
			return MarkdownSource.create(resourceRef, replaceProperties);
		} else {
			return new JavaSource(resourceRef, replaceProperties);
		}
	}
}
