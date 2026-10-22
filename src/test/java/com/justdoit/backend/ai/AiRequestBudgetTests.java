package com.justdoit.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

// SRS 25: "only the description is included, static instruction unchanged between calls, reasoning disabled, output cap set"
class AiRequestBudgetTests {

	private static final String DESCRIPTION = "finish dbms assignment 3, about 4 hours";
	private final ObjectMapper json = new ObjectMapper();

	@Test
	@SuppressWarnings("unchecked")
	void geminiRequestCarriesOnlyTheDescriptionWithThinkingOffAndCappedOutput() throws Exception {
		GeminiProvider gemini = new GeminiProvider("key", "gemini-3.5-flash-lite");
		Map<String, Object> body = gemini.requestBody(DESCRIPTION);
		Map<String, Object> config = (Map<String, Object>) body.get("generationConfig");

		assertThat(config).containsEntry("maxOutputTokens", 80).containsEntry("temperature", 0)
				.containsEntry("thinkingConfig", Map.of("thinkingLevel", "minimal"));
		assertThat(json.writeValueAsString(body.get("contents"))).isEqualTo(
				"[{\"role\":\"user\",\"parts\":[{\"text\":\"" + DESCRIPTION + "\"}]}]");
		assertThat(gemini.requestBody("something else").get("systemInstruction")).isEqualTo(body.get("systemInstruction"));

		// Gemini 2.x uses a budget of 0 instead; never both
		assertThat(new GeminiProvider("key", "gemini-2.5-flash").thinkingOff()).isEqualTo(Map.of("thinkingBudget", 0));
	}

	@Test
	@SuppressWarnings("unchecked")
	void groqRequestCarriesOnlyTheDescriptionWithReasoningOffAndCappedOutput() {
		Map<String, Object> body = new GroqProvider("key", "qwen/qwen3.8-27b").requestBody(DESCRIPTION);
		List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");

		assertThat(body).containsEntry("max_completion_tokens", 80).containsEntry("temperature", 0)
				.containsEntry("reasoning_effort", "none");
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).containsEntry("role", "system").containsEntry("content", GroqProvider.INSTRUCTION);
		assertThat(messages.get(1)).isEqualTo(Map.of("role", "user", "content", DESCRIPTION));
	}

	@Test
	void staticInstructionStaysShort() {
		// ~4 characters per token: keeps the fixed overhead near the 60-token target (SRS 8.6 rule 3)
		assertThat(AiPrompt.INSTRUCTION.length()).isLessThan(260);
	}

}
