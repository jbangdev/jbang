package dev.jbang.cli;

import org.aesh.command.option.Option;

public class AIOptions {

	@Option(name = "ai-provider", description = "Provider to use (openai, openrouter, etc.) when using init with a prompt")
	String provider;

	@Option(name = "ai-api-key", description = "API Key/token to use for the AI Provider")
	String apiKey;

	@Option(name = "ai-endpoint", description = "OpenAI compatible Endpoint to use for the AI Provider when using init with a prompt")
	String endpoint;

	@Option(name = "ai-model", description = "Model string to use for init prompting")
	String model;
}
