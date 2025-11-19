package dev.jbang.ai;

import java.io.IOException;

/**
 * Base interface for AI providers that can generate code using
 * OpenAI-compatible chat completions API.
 */
public interface AIProvider {

	/**
	 * Generate code using the AI provider.
	 *
	 * @param baseName    The base name for the generated class
	 * @param extension   The file extension (e.g., "java", "kt")
	 * @param request     The user's request/description
	 * @param javaVersion The requested Java version (e.g., "17+", "21") or null if
	 *                    not specified
	 * @return The generated code
	 * @throws IOException if there's a problem communicating with the API
	 */
	String generateCode(String baseName, String extension, String request, String javaVersion) throws IOException;

	/**
	 * Get the name of this provider (e.g., "OpenAI", "OpenRouter")
	 *
	 * @return The provider name
	 */
	String getName();

	String getModel();
}
