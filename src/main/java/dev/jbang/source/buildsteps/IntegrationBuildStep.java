package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.spi.IntegrationManager;
import dev.jbang.spi.IntegrationResult;

/**
 * This class takes a <code>Project</code> and the result of a previous
 * "compile" step and runs any integrations that might be found. Those
 * integration can make changes to the project that will be used as the input
 * for the next build step.
 */
public class IntegrationBuildStep implements Builder<IntegrationResult> {
	private final BuildContext ctx;

	public IntegrationBuildStep(BuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public IntegrationResult build() throws IOException {
		// todo: setting properties to avoid loosing properties in integration call.
		Project project = ctx.getProject();
		Properties old = System.getProperties();
		Properties temp = new Properties(System.getProperties());
		for (Map.Entry<String, String> entry : project.getProperties().entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}
		IntegrationResult integrationResult = IntegrationManager.runIntegrations(ctx);
		System.setProperties(old);

		if (project.getMainClass() == null) { // if non-null user forced set main
			if (integrationResult.mainClass != null) {
				project.setMainClass(integrationResult.mainClass);
			}
		}
		if (integrationResult.javaArgs != null && !integrationResult.javaArgs.isEmpty()) {
			// Add integration options to the java options
			project.addRuntimeOptions(integrationResult.javaArgs);
		}
		return integrationResult;
	}
}
