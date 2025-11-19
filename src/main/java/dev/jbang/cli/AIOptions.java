package dev.jbang.cli;

import picocli.CommandLine;

public class AIOptions {

	@CommandLine.Option(names = {
			"--ai-provider" }, description = "Provider to use (openai, openrouter, etc.) when using init with a prompt - if none specified will use the default provider from environment variables", defaultValue = "${JBANG_AI_PROVIDER:-${default.ai.provider)}")
	String provider;
	@CommandLine.Option(names = {
			"--ai-api-key" }, description = "API Key/token to use for the AI Provider", defaultValue = "${JBANG_AI_API_KEY:-${default.ai.api-key)}")
	String apiKey;
	@CommandLine.Option(names = {
			"--ai-endpoint" }, description = "OpenAI compatible Endpoint to use for the AI Provider when using init with a prompt", defaultValue = "${JBANG_AI_ENDPOINT:-${default.ai.endpoint)}")
	String endpoint;
	@CommandLine.Option(names = {
			"--ai-model" }, description = "Model string to ue for init prompting", defaultValue = "${JBANG_AI_MODEL:-${default.ai.model}}")
	String model;

}
