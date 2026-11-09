package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import dev.jbang.ExitException;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.MainClassScanner;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

/**
 * This class takes a <code>Project</code> and compiles it.
 */
public abstract class CompileBuildStep implements Builder<Project> {
	protected final BuildContext ctx;

	public CompileBuildStep(BuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public Project build() throws IOException {
		return compile();
	}

	protected Project compile() throws IOException {
		List<String> compileCmd = getCompileCommand();

		// add additional files
		Project project = ctx.getProject();
		project.getMainSourceSet().copyResourcesTo(ctx.getCompileDir());

		generatePom();

		Util.infoMsg(String.format("Building %s for %s...", project.getMainSource().isAgent() ? "javaagent" : "jar",
				project.getResourceRef().getFile().getFileName().toString()));
		Util.verboseMsg("Compile: " + String.join(" ", compileCmd));
		runCompiler(compileCmd);

		searchForMain(ctx.getCompileDir());

		return project;
	}

	protected List<String> getCompileCommand() throws IOException {
		List<String> compileCmd = new ArrayList<>();

		Project project = ctx.getProject();
		compileCmd.add(getCompilerBinary());
		compileCmd.addAll(getCompileCommandOptions());

		// add source files to compile
		compileCmd.addAll(project.getMainSourceSet()
			.getSources()
			.stream()
			.map(x -> x.getFile().toString())
			.collect(Collectors.toList()));

		if (project.getModuleName().isPresent()) {
			if (project.getMainSource() != null && !project.getMainSource().getJavaPackage().isPresent()) {
				throw new ExitException(ExitException.EXIT_INVALID_INPUT,
						"Module code cannot work with the default package, adding a 'package' statement is required");
			}
			if (!hasModuleInfoFile()) {
				// generate module-info descriptor and add it to list of files to compile
				Path infoFile = ModuleUtil.generateModuleInfo(ctx);
				if (infoFile != null) {
					compileCmd.add(infoFile.toString());
				}
			}
		}

		return compileCmd;
	}

	private boolean hasModuleInfoFile() {
		return ctx.getProject()
			.getMainSourceSet()
			.getSources()
			.stream()
			.anyMatch(s -> s.getFile().getFileName().toString().equals("module-info.java"));
	}

	protected abstract String getCompilerBinary();

	protected abstract List<String> getCompileCommandOptions() throws IOException;

	protected void runCompiler(List<String> optionList) throws IOException {
		runCompiler(CommandBuffer.of(optionList)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder()
			.inheritIO());
	}

	protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
		Process process = processBuilder.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during compile");
		}
	}

	protected Path generatePom() throws IOException {
		Template pomTemplate = TemplateEngine.instance()
			.getTemplate(ResourceRef.forResource("classpath:/pom.qute.xml"));

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			Project project = ctx.getProject();
			String baseName = Util.getBaseName(project.getResourceRef().getFile().getFileName().toString());
			MavenCoordinate gav = getPomGav(project);

			List<ArtifactInfo> pomDeps = getDirectDependencies(project);
			List<Map<String, Object>> managedDeps = extractManagedExclusions(pomDeps);

			String pomfile = pomTemplate
				.data("baseName", baseName)
				.data("group", gav.getGroupId())
				.data("artifact", gav.getArtifactId())
				.data("version", gav.getVersion())
				.data("description", project.getDescription().orElse(""))
				.data("dependencies", pomDeps)
				.data("managedDeps", managedDeps)
				.render();

			pomPath = getPomPath(ctx);
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}

		return pomPath;
	}

	private List<Map<String, Object>> extractManagedExclusions(List<ArtifactInfo> directDeps) {
		Map<String, String> resolvedVersions = new HashMap<>();
		for (ArtifactInfo art : ctx.resolveClassPath().getArtifacts()) {
			if (art.getCoordinate() != null) {
				String key = art.getCoordinate().getGroupId() + ":" + art.getCoordinate().getArtifactId();
				resolvedVersions.put(key, art.getCoordinate().getVersion());
			}
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (ArtifactInfo dep : directDeps) {
			Path jarPath = dep.getFile();
			String jarName = jarPath.getFileName().toString();
			if (!jarName.endsWith(".jar")) {
				continue;
			}
			Path pomPath = jarPath.getParent()
				.resolve(jarName.substring(0, jarName.length() - 4) + ".pom");
			if (!Files.exists(pomPath)) {
				continue;
			}
			result.addAll(parsePomExclusions(pomPath, resolvedVersions));
		}
		return result;
	}

	private List<Map<String, Object>> parsePomExclusions(Path pomPath, Map<String, String> resolvedVersions) {
		List<Map<String, Object>> result = new ArrayList<>();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc;
			try (InputStream is = Files.newInputStream(pomPath)) {
				doc = builder.parse(is);
			}
			NodeList depNodes = doc.getElementsByTagName("dependency");
			for (int i = 0; i < depNodes.getLength(); i++) {
				Element depElem = (Element) depNodes.item(i);
				if (depElem.getParentNode() == null
						|| !"dependencies".equals(depElem.getParentNode().getNodeName())) {
					continue;
				}
				if (isInsideDependencyManagement(depElem)) {
					continue;
				}
				NodeList exclusionNodes = depElem.getElementsByTagName("exclusion");
				if (exclusionNodes.getLength() == 0) {
					continue;
				}
				String groupId = getChildText(depElem, "groupId");
				String artifactId = getChildText(depElem, "artifactId");
				if (groupId == null || artifactId == null) {
					continue;
				}
				String version = resolvedVersions.get(groupId + ":" + artifactId);
				if (version == null) {
					continue;
				}
				List<Map<String, String>> exclusions = new ArrayList<>();
				for (int j = 0; j < exclusionNodes.getLength(); j++) {
					Element exElem = (Element) exclusionNodes.item(j);
					String exGroupId = getChildText(exElem, "groupId");
					String exArtifactId = getChildText(exElem, "artifactId");
					if (exGroupId != null && exArtifactId != null) {
						Map<String, String> ex = new HashMap<>();
						ex.put("groupId", exGroupId);
						ex.put("artifactId", exArtifactId);
						exclusions.add(ex);
					}
				}
				if (!exclusions.isEmpty()) {
					Map<String, Object> md = new HashMap<>();
					md.put("groupId", groupId);
					md.put("artifactId", artifactId);
					md.put("version", version);
					md.put("exclusions", exclusions);
					result.add(md);
				}
			}
		} catch (Exception e) {
			Util.verboseMsg("Could not parse POM for exclusions: " + pomPath + " - " + e.getMessage());
		}
		return result;
	}

	private static boolean isInsideDependencyManagement(Element elem) {
		org.w3c.dom.Node parent = elem.getParentNode();
		while (parent != null) {
			if (parent instanceof Element && "dependencyManagement".equals(((Element) parent).getTagName())) {
				return true;
			}
			parent = parent.getParentNode();
		}
		return false;
	}

	private static String getChildText(Element parent, String tagName) {
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i) instanceof Element) {
				Element elem = (Element) nodes.item(i);
				if (tagName.equals(elem.getTagName())) {
					return elem.getTextContent().trim();
				}
			}
		}
		return null;
	}

	private List<ArtifactInfo> getDirectDependencies(Project project) {
		Set<String> directDepKeys = project.getMainSourceSet()
			.getDependencies()
			.stream()
			.map(MavenCoordinate::fromString)
			.filter(c -> !"pom".equalsIgnoreCase(c.getType()))
			.map(c -> c.getGroupId() + ":" + c.getArtifactId())
			.collect(Collectors.toSet());

		return ctx.resolveClassPath()
			.getArtifacts()
			.stream()
			.filter(a -> a.getCoordinate() != null
					&& directDepKeys.contains(
							a.getCoordinate().getGroupId() + ":" + a.getCoordinate().getArtifactId()))
			.collect(Collectors.toList());
	}

	private static final String QUARKUS_APP_GROUP_ID = "dev.jbang.user";
	private static final String QUARKUS_APP_ARTIFACT_ID = "quarkus";

	private static MavenCoordinate getPomGav(Project prj) {
		if (prj.getGav().isPresent()) {
			return MavenCoordinate.fromString(prj.getGav().get()).withVersion();
		} else if (hasQuarkusDependencies(prj)) {
			return new MavenCoordinate(QUARKUS_APP_GROUP_ID, QUARKUS_APP_ARTIFACT_ID,
					MavenCoordinate.DEFAULT_VERSION);
		} else {
			String baseName = Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
			return new MavenCoordinate(MavenCoordinate.DUMMY_GROUP, baseName, MavenCoordinate.DEFAULT_VERSION);
		}
	}

	private static boolean hasQuarkusDependencies(Project prj) {
		return prj.getMainSourceSet()
			.getDependencies()
			.stream()
			.map(MavenCoordinate::fromString)
			.anyMatch(c -> c.getGroupId() != null && c.getGroupId().startsWith("io.quarkus"));
	}

	public static Path getPomPath(BuildContext ctx) {
		MavenCoordinate gav = getPomGav(ctx.getProject());
		return ctx.getCompileDir()
			.resolve("META-INF/maven/" + gav.getGroupId().replace(".", "/") + "/pom.xml");
	}

	protected void searchForMain(Path tmpJarDir) {
		try {
			// using Files.walk method with try-with-resources
			try (Stream<Path> paths = Files.walk(tmpJarDir)) {
				List<Path> items = paths.filter(Files::isRegularFile)
					.filter(f -> !f.toFile().getName().contains("$"))
					.filter(f -> f.toFile().getName().endsWith(".class"))
					.collect(Collectors.toList());

				MainClassScanner.MainScan scan = MainClassScanner.scan(consumer -> {
					for (Path item : items) {
						try (InputStream stream = Files.newInputStream(item)) {
							consumer.accept(item.toString(), stream);
						}
					}
				});

				Project project = ctx.getProject();
				if (project.getMainClass() == null) { // if non-null user forced set main
					List<String> mains = scan.getMainClasses();
					String mainName = getSuggestedMain();
					if (mains.size() > 1 && mainName != null) {
						List<String> suggestedmain = mains.stream()
							.filter(n -> simpleName(n).equals(mainName))
							.collect(Collectors.toList());
						if (!suggestedmain.isEmpty()) {
							mains = suggestedmain;
						}
					}

					if (!mains.isEmpty()) {
						project.setMainClass(mains.get(0));
						if (mains.size() > 1) {
							Util.warnMsg(
									"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
											+ String.join(",", mains));
						}
					}
				}

				if (project.getMainSource().isAgent()) {
					scan.getAgentMain().ifPresent(project::setAgentMainClass);
					scan.getPreMain().ifPresent(project::setPreMainClass);
				}
			}
		} catch (IOException e) {
			throw new ExitException(1, e);
		}
	}

	protected String getSuggestedMain() {
		Project project = ctx.getProject();
		if (!project.getResourceRef().isStdin()) {
			return project.getResourceRef().getFile().getFileName().toString().replace("." + getMainExtension(), "");
		} else {
			return null;
		}
	}

	protected abstract String getMainExtension();

	private static String simpleName(String fqcn) {
		int i = fqcn.lastIndexOf('.');
		return i < 0 ? fqcn : fqcn.substring(i + 1);
	}
}
