package com.justdoit.backend.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.dto.TaskSuggestion;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.exception.ApiException;

// The single AI integration point (SRS 8.4). Tries each configured provider once, in @Order
// (Gemini, then Groq), and returns the first valid suggestion. Nothing here is ever saved.
@Service
public class AiService {

	private static final Logger log = LoggerFactory.getLogger(AiService.class);
	private static final BigDecimal MIN_HOURS = new BigDecimal("0.1");
	private static final BigDecimal MAX_HOURS = new BigDecimal("999.9");
	private static final int MAX_TITLE = 200;

	private final List<AiProvider> providers;
	private final ObjectMapper json;

	public AiService(List<AiProvider> providers, ObjectMapper json) {
		this.providers = providers;
		this.json = json;
	}

	// description: already trimmed, whitespace-collapsed and <= 500 chars (TaskService checks)
	public TaskSuggestion suggest(String description) {
		for (AiProvider provider : providers) {
			if (!provider.isConfigured()) {
				continue;
			}
			long started = System.currentTimeMillis();
			AiReply reply = null;
			String outcome;
			try {
				reply = provider.complete(description);
				if (reply.truncated()) {
					outcome = "TRUNCATED";
				}
				else {
					TaskSuggestion suggestion = normalize(reply.text());
					log(provider, reply, "OK", started);
					return suggestion;
				}
			}
			catch (InvalidReplyException e) {
				outcome = "INVALID(" + e.getMessage() + ")";
			}
			catch (RuntimeException e) {
				outcome = "ERROR(" + e.getClass().getSimpleName() + ")";
			}
			log(provider, reply, outcome, started);
		}
		// Every provider failed or none is configured: the app opens the manual form (SRS 8.5)
		throw ApiException.aiUnavailable();
	}

	// SRS 8.6 rule 8: the backend, not the model, cleans the answer. Only what can't be cleaned is invalid.
	TaskSuggestion normalize(String text) {
		if (text == null || text.isBlank()) {
			throw new InvalidReplyException("empty");
		}
		JsonNode node;
		try {
			node = json.readTree(stripCodeFence(text));
		}
		catch (Exception e) {
			throw new InvalidReplyException("not JSON");
		}
		if (node == null || !node.isObject()) {
			throw new InvalidReplyException("not an object");
		}

		String title = node.path("title").asText("").strip().replaceAll("\\s+", " ");
		if (title.isEmpty()) {
			throw new InvalidReplyException("no title");
		}
		if (title.length() > MAX_TITLE) {
			title = title.substring(0, MAX_TITLE).strip();
		}

		return new TaskSuggestion(title, category(node.path("category").asText("")), hours(node.path("hours")));
	}

	// Out-of-vocabulary is not a failure (SRS 8.5): it becomes UNCATEGORIZED
	private static Category category(String raw) {
		try {
			return Category.valueOf(raw.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return Category.UNCATEGORIZED;
		}
	}

	// One decimal, kept inside the range the task form accepts. Missing, non-numeric or <= 0 is invalid.
	private static BigDecimal hours(JsonNode node) {
		BigDecimal hours;
		try {
			hours = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().strip());
		}
		catch (NumberFormatException e) {
			throw new InvalidReplyException("hours not a number");
		}
		if (hours.signum() <= 0) {
			throw new InvalidReplyException("hours not positive");
		}
		hours = hours.setScale(1, RoundingMode.HALF_UP);
		return hours.max(MIN_HOURS).min(MAX_HOURS);
	}

	// Some models wrap JSON in ```json ... ``` even in JSON mode
	private static String stripCodeFence(String text) {
		String t = text.strip();
		if (t.startsWith("```")) {
			t = t.substring(t.indexOf('\n') + 1);
			if (t.endsWith("```")) {
				t = t.substring(0, t.length() - 3);
			}
		}
		return t;
	}

	// SRS 8.6 rule 10: measure every call. The description is never logged.
	private static void log(AiProvider provider, AiReply reply, String outcome, long started) {
		log.info("AI call provider={} model={} outcome={} promptTokens={} outputTokens={} reasoningTokens={} latencyMs={}",
				provider.name(), provider.model(), outcome,
				reply == null ? null : reply.promptTokens(), reply == null ? null : reply.outputTokens(),
				reply == null ? null : reply.reasoningTokens(), System.currentTimeMillis() - started);
	}

	static final class InvalidReplyException extends RuntimeException {
		InvalidReplyException(String reason) {
			super(reason);
		}
	}

}
