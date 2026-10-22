package com.justdoit.backend.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

// Fallback provider (SRS 8.4), OpenAI-compatible chat API. Plain JSON mode: on 2026-10-22 the strict
// json_schema format hung on qwen/qwen3.8-27b, so the category list goes in the instruction instead.
@Component
@Order(2)
public class GroqProvider implements AiProvider {

	static final String INSTRUCTION = AiPrompt.INSTRUCTION + " category is one of: " + String.join(", ", AiPrompt.CATEGORIES) + ".";

	private final String apiKey;
	private final String model;
	private final RestClient client;

	public GroqProvider(@Value("${ai.groq.api-key}") String apiKey, @Value("${ai.groq.model}") String model) {
		this.apiKey = apiKey;
		this.model = model;
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(5_000);
		timeouts.setReadTimeout(15_000);
		this.client = RestClient.builder()
				.baseUrl("https://api.groq.com/openai/v1")
				.requestFactory(timeouts)
				.build();
	}

	@Override
	public String name() {
		return "groq";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public boolean isConfigured() {
		return !apiKey.isBlank();
	}

	@Override
	public AiReply complete(String description) {
		JsonNode response = client.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody(description))
				.retrieve()
				.body(JsonNode.class);

		JsonNode choice = response.path("choices").path(0);
		JsonNode usage = response.path("usage");
		String text = choice.path("message").path("content").asText(null);
		return new AiReply(text, "length".equals(choice.path("finish_reason").asText()),
				intOrNull(usage, "prompt_tokens"), intOrNull(usage, "completion_tokens"),
				usage.path("completion_tokens_details").path("reasoning_tokens").asInt(0));
	}

	Map<String, Object> requestBody(String description) {
		return Map.of(
				"model", model,
				"temperature", 0,
				"max_completion_tokens", AiPrompt.MAX_OUTPUT_TOKENS,
				// SRS 8.6 rule 5: this model supports switching reasoning fully off
				"reasoning_effort", "none",
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", INSTRUCTION),
						Map.of("role", "user", "content", description)));
	}

	private static Integer intOrNull(JsonNode node, String field) {
		return node.hasNonNull(field) ? node.get(field).asInt() : null;
	}

}
