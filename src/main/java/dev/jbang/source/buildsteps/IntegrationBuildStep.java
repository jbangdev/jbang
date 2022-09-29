package dev.jbang.source.buildsteps;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import dev.jbang.cli.ExitException;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.spi.IntegrationManager;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.Util;

/**
 * This class takes a <code>Project</code> and the result of a previous
 * "compile" step and runs any integrations that might be found. Those
 * integration can make changes to the project that will be used as the input
 * for the next build step.
 */
public abstract class IntegrationBuildStep implements Builder<IntegrationResult> {
	private final Project project;

	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);

	public IntegrationBuildStep(Project project) {
		this.project = project;
	}

	@Override
	public IntegrationResult build() throws IOException {
		// todo: setting properties to avoid loosing properties in integration call.
		Path compileDir = project.getBuildDir();
		Path pomPath = CompileBuildStep.getPomPath(project);
		Properties old = System.getProperties();
		Properties temp = new Properties(System.getProperties());
		for (Map.Entry<String, String> entry : project.getProperties().entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}
		IntegrationResult integrationResult = IntegrationManager.runIntegrations(project, compileDir, pomPath);
		System.setProperties(old);

		if (project.getMainClass() == null) { // if non-null user forced set main
			if (integrationResult.mainClass != null) {
				project.setMainClass(integrationResult.mainClass);
			} else {
				searchForMain(compileDir);
			}
		}
		if (integrationResult.javaArgs != null && !integrationResult.javaArgs.isEmpty()) {
			// Add integration options to the java options
			project.addRuntimeOptions(integrationResult.javaArgs);
		}
		return integrationResult;
	}

	protected void searchForMain(Path tmpJarDir) {
		try {
			// using Files.walk method with try-with-resources
			try (Stream<Path> paths = Files.walk(tmpJarDir)) {
				List<Path> items = paths.filter(Files::isRegularFile)
										.filter(f -> !f.toFile().getName().contains("$"))
										.filter(f -> f.toFile().getName().endsWith(".class"))
										.collect(Collectors.toList());

				Indexer indexer = new Indexer();
				Index index;
				for (Path item : items) {
					try (InputStream stream = new FileInputStream(item.toFile())) {
						indexer.index(stream);
					}
				}
				index = indexer.complete();

				Collection<ClassInfo> classes = index.getKnownClasses();

				List<ClassInfo> mains = classes	.stream()
												.filter(getMainFinder())
												.collect(Collectors.toList());
				String mainName = getSuggestedMain();
				if (mains.size() > 1 && mainName != null) {
					List<ClassInfo> suggestedmain = mains	.stream()
															.filter(ci -> ci.simpleName().equals(mainName))
															.collect(Collectors.toList());
					if (!suggestedmain.isEmpty()) {
						mains = suggestedmain;
					}
				}

				if (!mains.isEmpty()) {
					project.setMainClass(mains.get(0).name().toString());
					if (mains.size() > 1) {
						Util.warnMsg(
								"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
										+ mains	.stream()
												.map(x -> x.name().toString())
												.collect(Collectors.joining(",")));
					}
				}

				if (project.getMainSource().isAgent()) {
					Optional<ClassInfo> agentmain = classes	.stream()
															.filter(pubClass -> pubClass.method("agentmain",
																	STRINGTYPE,
																	INSTRUMENTATIONTYPE) != null
																	||
																	pubClass.method("agentmain",
																			STRINGTYPE) != null)
															.findFirst();

					if (agentmain.isPresent()) {
						project.setAgentMainClass(agentmain.get().name().toString());
					}

					Optional<ClassInfo> premain = classes	.stream()
															.filter(pubClass -> pubClass.method("premain",
																	STRINGTYPE,
																	INSTRUMENTATIONTYPE) != null
																	||
																	pubClass.method("premain",
																			STRINGTYPE) != null)
															.findFirst();

					if (premain.isPresent()) {
						project.setPreMainClass(premain.get().name().toString());
					}
				}
			}
		} catch (IOException e) {
			throw new ExitException(1, e);
		}
	}

	protected String getSuggestedMain() {
		if (!project.getResourceRef().isStdin()) {
			return project.getResourceRef().getFile().getFileName().toString().replace(getMainExtension(), "");
		} else {
			return null;
		}
	}

	protected abstract String getMainExtension();

	protected Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", STRINGARRAYTYPE) != null;
	}
}
