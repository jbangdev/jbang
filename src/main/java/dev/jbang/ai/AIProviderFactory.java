package dev.jbang.ai;

import static java.lang.System.getenv;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import dev.jbang.util.Util;

/**
 * Factory for creating AI providers based on available API keys.
 * 
 * <p>
 * Supports 15+ AI providers with automatic detection from environment
 * variables. When a provider is explicitly set, it takes priority over
 * auto-detection. Provider configurations are initialized once at class load
 * time for optimal performance.
 * </p>
 */
public class AIProviderFactory {

	private static final Map<String, ProviderConfig> PROVIDER_MAP = createProviderMap();

	private String defaultProvider;
	private String defaultKey;
	private String defaultEndpoint;
	private String defaultModel;

	public AIProviderFactory() {
		this(null, null, null, null);
	}

	public AIProviderFactory(String defaultProvider, String defaultKey, String defaultEndpoint, String defaultModel) {
		this.defaultProvider = defaultProvider;
		this.defaultKey = defaultKey;
		this.defaultEndpoint = defaultEndpoint;
		this.defaultModel = defaultModel;
	}

	private static Map<String, ProviderConfig> createProviderMap() {
		Map<String, ProviderConfig> map = new LinkedHashMap<>();
		// Build provider configurations in order (LinkedHashMap maintains insertion
		// order)
		// order below is more or less arbitrary as those are all fairly unique keys
		map.put("openai", new ProviderConfig("OPENAI_API_KEY", "openai", "https://api.openai.com/v1",
				"gpt-5.1"));
		map.put("openrouter", new ProviderConfig("OPENROUTER_API_KEY", "openrouter",
				"https://openrouter.ai/api/v1", "x-ai/grok-code-fast-1"));
		// map.put("anthropic", new ProviderConfig("CLAUDE_API_KEY", "anthropic",
		// "https://api.anthropic.com/v1", "claude-3.5-haiku"));
		map.put("google", new ProviderConfig("GEMINI_API_KEY", "google",
				"https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3-pro-preview"));
		// map.put("groq", new ProviderConfig("GROQ_API_KEY", "groq",
		// "https://api.groq.com/openai/v1",
		// "llama-3.1-70b-versatile"));
		// map.put("together", new ProviderConfig("TOGETHER_API_KEY", "together",
		// "https://api.together.xyz/v1", "meta-llama/Llama-3-70b-chat-hf"));
		// map.put("mistral", new ProviderConfig("MISTRAL_API_KEY", "mistral",
		// "https://api.mistral.ai/v1",
		// "mistral-large-latest"));
		// map.put("deepinfra", new ProviderConfig("DEEPINFRA_API_KEY", "deepinfra",
		// "https://api.deepinfra.com/v1/openai", "meta-llama/Llama-3-70b-chat-hf"));
		map.put("opencode", new ProviderConfig("OPENCODE_API_KEY", "opencode",
				"https://opencode.ai/zen/v1", "gpt-5.1"));
		// map.put("fireworks", new ProviderConfig("FIREWORKS_API_KEY", "fireworks",
		// "https://api.fireworks.ai/inference/v1",
		// "accounts/fireworks/models/llama-v3-70b-instruct"));
		// map.put("anyscale", new ProviderConfig("ANYSCALE_API_KEY", "anyscale",
		// "https://api.endpoints.anyscale.com/v1", "meta-llama/Llama-3-70b-chat-hf"));
		// map.put("huggingface", new ProviderConfig("HUGGINGFACE_API_KEY",
		// "huggingface",
		// "https://api-inference.huggingface.co/v1",
		// "meta-llama/Llama-3-70b-chat-hf"));
		// map.put("perplexity", new ProviderConfig("PERPLEXITY_API_KEY", "perplexity",
		// "https://api.perplexity.ai", "llama-3-sonar-large-32k-online"));

		// putting github last as GITHUB_TOKEN could be a personal access token or a PAT
		// for many other things.
		map.put("github", new ProviderConfig("GITHUB_TOKEN", "github",
				"https://models.github.ai/inference", "openai/gpt-4.1"));
		map.put("ollama", new ProviderConfig(() -> null, "ollama", "http://localhost:11434/v1", "llama3"));
		return map;
	}

	public AIProvider createProvider(String provider, String key, String endpoint, String model) {
		return new OpenAIProvider(
				Objects.toString(defaultProvider, provider),
				Objects.toString(defaultKey, key),
				Objects.toString(defaultEndpoint, endpoint),
				Objects.toString(defaultModel, model));
	}

	/**
	 * Create an AI provider based on available environment variables.
	 * 
	 * <p>
	 * If a provider is explicitly set (via defaultProvider), it will be used
	 * regardless of which API keys are detected. This ensures predictable behavior
	 * when a specific provider is requested.
	 * </p>
	 * 
	 * <p>
	 * Otherwise, auto-detection checks providers in order (first match wins):
	 * OpenAI, OpenRouter, Google (Gemini), OpenCode Zen, GitHub (checked last due
	 * to potential token ambiguity), Ollama (only when explicitly set).
	 * </p>
	 *
	 * @return An AI provider if a valid API key is found, null otherwise
	 */
	public AIProvider createProvider() {
		// If provider is explicitly set, use it
		if (defaultProvider != null && !defaultProvider.trim().isEmpty()) {
			Util.verboseMsg("Using explicitly set provider: " + defaultProvider);
			ProviderConfig config = PROVIDER_MAP.get(defaultProvider.toLowerCase());
			if (config != null) {
				AIProvider provider = config.createProvider(this);
				if (provider != null) {
					return provider;
				} else {
					Util.verboseMsg("No provider could be created for explicitly set provider: " + defaultProvider);
					return null;
				}
			} else {
				Util.verboseMsg("No provider config found for explicitly setprovider: " + defaultProvider);
				return null;
			}
		}

		for (ProviderConfig config : PROVIDER_MAP.values()) {
			AIProvider provider = config.createProvider(this);
			if (config.canCreateProvider(this)) {
				return provider;
			}
		}
		return null;
	}

	private static class ProviderConfig {
		private final Supplier<String> keySupplier;
		private final String providerName;
		private final String endpoint;
		private final String model;

		ProviderConfig(String envVar, String providerName, String endpoint, String model) {
			this(() -> getenv(envVar), providerName, endpoint, model);
		}

		ProviderConfig(Supplier<String> keySupplier, String providerName, String endpoint, String model) {
			this.keySupplier = keySupplier;
			this.providerName = providerName;
			this.endpoint = endpoint;
			this.model = model;
		}

		public boolean canCreateProvider(AIProviderFactory aiProviderFactory) {
			if (aiProviderFactory.defaultKey != null && !aiProviderFactory.defaultKey.trim().isEmpty()) {
				return true;
			} else {
				return keySupplier.get() != null && !keySupplier.get().trim().isEmpty();
			}
		}

		AIProvider createProvider(AIProviderFactory factory) {
			return factory.createProvider(providerName, keySupplier.get(), endpoint, model);
		}
	}
}
