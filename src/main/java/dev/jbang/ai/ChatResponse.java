package dev.jbang.ai;

import java.util.List;
import java.util.Map;

/**
 * Response model for OpenAI-compatible chat completion APIs.
 */
public class ChatResponse {
	public String id;
	public String object;
	public double created;
	public String model;
	public Usage usage;
	public List<Choice> choices;

	public static class Usage {
		public Integer prompt_tokens;
		public Integer completion_tokens;
		public Integer total_tokens;
		public Map<String, Object> prompt_tokens_details;
		public Map<String, Object> completion_tokens_details;
	}

	public static class Choice {
		public Message message;

		public static class Message {
			public String role;
			public String content;
		}
	}

	public Error error;

	public static class Error {
		String message;
		String type;
		String param;
		String code;

		@Override
		public String toString() {
			return type + ": " + message + " (code:" + code + "/param:" + param + ")";
		}
	}
}
