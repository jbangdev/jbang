package dev.jbang.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.google.gson.Gson;

import dev.jbang.resources.ResourceRef;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

/**
 * Implementation for AI providers using OpenAI-compatible API.
 */
public class OpenAIProvider implements AIProvider {

	private final String name;
	private final String apiKey;
	private final String endpoint;
	private final String defaultModel;

	public OpenAIProvider(String name, String apiKey, String endpoint, String defaultModel) {
		this.name = name;
		this.apiKey = apiKey;
		this.endpoint = endpoint;
		this.defaultModel = defaultModel;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String generateCode(String baseName, String extension, String request, String javaVersion)
			throws IllegalStateException, IOException {
		String answer = null;
		URL url = new URL(endpoint + "/chat/completions");
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		httpConn.setRequestMethod("POST");

		httpConn.setRequestProperty("Content-Type", "application/json");
		if (apiKey != null && !apiKey.trim().isEmpty()) {
			httpConn.setRequestProperty("Authorization", "Bearer " + apiKey);
		}

		httpConn.setDoOutput(true);
		OutputStreamWriter writer = new OutputStreamWriter(httpConn.getOutputStream());

		Map<String, Object> prompt = new HashMap<>();
		prompt.put("model", getModel());
		prompt.put("temperature", 0.8); // reduce variation, more deterministic
		Gson gson = new Gson();

		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(createPrompt("system", buildSystemPrompt(baseName, extension, javaVersion)));
		messages.add(createPrompt("user", request));
		prompt.put("messages", messages);
		String requestJson = gson.toJson(prompt);
		Util.verboseMsg(getName() + " endpoint: " + url + " request: "
				+ requestJson);
		writer.write(requestJson);
		writer.flush();
		writer.close();
		httpConn.getOutputStream().close();

		boolean success = httpConn.getResponseCode() / 100 == 2;

		InputStream responseStream = success
				? httpConn.getInputStream()
				: httpConn.getErrorStream();

		String response;
		try (Scanner s = new Scanner(responseStream).useDelimiter("\\A")) {
			response = s.hasNext() ? s.next() : "";
		}
		Util.verboseMsg(getName() + " response: " + response);

		if (!success) {
			throw new IllegalStateException("Received no useful response from " + getName() + ", status: "
					+ httpConn.getResponseCode() + ". Usage limit exceeded or wrong key? "
					+ response);
		}

		ChatResponse result = gson.fromJson(response, ChatResponse.class);
		if (result.choices != null && result.error == null) {
			answer = result.choices.stream()
				.map(c -> c.message.content)
				.collect(java.util.stream.Collectors.joining("\n"));
		} else {
			throw new IllegalStateException(
					"Received no useful response from " + getName() + ". Usage limit exceeded or wrong key? "
							+ result.error);
		}

		return answer;
	}

	@Override
	public String getModel() {
		return defaultModel;
	}

	private static Map<String, String> createPrompt(String role, String content) {
		Map<String, String> m = new HashMap<>();
		m.put("role", role);
		m.put("content", content);
		return m;
	}

	private String buildSystemPrompt(String baseName, String extension, String javaVersion) {
		String extLower = extension.toLowerCase();
		boolean isJava = extLower.equals(".java");
		boolean isKotlin = extLower.equals(".kt");
		boolean isGroovy = extLower.equals(".groovy");

		Template template = TemplateEngine.instance()
			.getTemplate(ResourceRef.forResource("classpath:/ai-system-prompt.qute"));
		if (template == null) {
			throw new IllegalStateException("Could not load AI system prompt template");
		}

		TemplateInstance instance = template.instance()
			.data("baseName", baseName)
			.data("extension", extension)
			.data("isJava", isJava)
			.data("isKotlin", isKotlin)
			.data("isGroovy", isGroovy)
			.data("javaVersion", javaVersion);

		return instance.render();
	}
}
