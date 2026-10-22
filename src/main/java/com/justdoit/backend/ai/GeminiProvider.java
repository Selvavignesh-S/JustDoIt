package com.justdoit.backend.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

// Primary provider (SRS 8.4). generateContent with a JSON response schema, temperature 0, output capped, thinking off.
@Component
@Order(1)
public class GeminiProvider implements AiProvider {

	private final String apiKey;
	private final String model;
	private final RestClient client;

	public GeminiProvider(@Value("${ai.gemini.api-key}") String apiKey, @Value("${ai.gemini.model}") String model) {
		this.apiKey = apiKey;
		this.model = model;
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(5_000);
		timeouts.setReadTimeout(20_000); // measured ~8 s for gemini-3.5-flash-lite on 2026-10-22
		this.client = RestClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com/v1beta")
				.requestFactory(timeouts)
				.build();
	}

	@Override
	public String name() {
		return "gemini";
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
				.uri("/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody(description))
				.retrieve()
				.body(JsonNode.class);

		JsonNode candidate = response.path("candidates").path(0);
		JsonNode usage = response.path("usageMetadata");
		String text = candidate.path("content").path("parts").path(0).path("text").asText(null);
		return new AiReply(text, "MAX_TOKENS".equals(candidate.path("finishReason").asText()),
				intOrNull(usage, "promptTokenCount"), intOrNull(usage, "candidatesTokenCount"),
				usage.path("thoughtsTokenCount").asInt(0));
	}

	Map<String, Object> requestBody(String description) {
		Map<String, Object> schema = Map.of(
				"type", "OBJECT",
				"properties", Map.of(
						"title", Map.of("type", "STRING"),
						"category", Map.of("type", "STRING", "enum", AiPrompt.CATEGORIES),
						"hours", Map.of("type", "NUMBER")),
				"required", List.of("title", "category", "hours"),
				"propertyOrdering", List.of("title", "category", "hours"));
		return Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", AiPrompt.INSTRUCTION))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", description)))),
				"generationConfig", Map.of(
						"temperature", 0,
						"maxOutputTokens", AiPrompt.MAX_OUTPUT_TOKENS,
						"responseMimeType", "application/json",
						"responseSchema", schema,
						"thinkingConfig", thinkingOff()));
	}

	// SRS 8.6 rule 5: Gemini 2.x turns thinking off with a budget of 0; Gemini 3+ rejects that and uses the
	// "minimal" level instead (measured 0 thinking tokens). The two must never be sent together.
	Map<String, Object> thinkingOff() {
		return model.startsWith("gemini-2") ? Map.of("thinkingBudget", 0) : Map.of("thinkingLevel", "minimal");
	}

	private static Integer intOrNull(JsonNode node, String field) {
		return node.hasNonNull(field) ? node.get(field).asInt() : null;
	}

}
