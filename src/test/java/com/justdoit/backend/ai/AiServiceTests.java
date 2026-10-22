package com.justdoit.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.dto.TaskSuggestion;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.exception.ApiException;

class AiServiceTests {

	// A provider whose answer the test decides; records whether it was called
	private static final class FakeProvider implements AiProvider {
		private final String name;
		private final boolean configured;
		private final Function<String, AiReply> answer;
		final List<String> calls = new ArrayList<>();

		FakeProvider(String name, boolean configured, Function<String, AiReply> answer) {
			this.name = name;
			this.configured = configured;
			this.answer = answer;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String model() {
			return name + "-model";
		}

		@Override
		public boolean isConfigured() {
			return configured;
		}

		@Override
		public AiReply complete(String description) {
			calls.add(description);
			return answer.apply(description);
		}
	}

	private static AiReply reply(String text) {
		return new AiReply(text, false, 70, 35, 0);
	}

	private static FakeProvider answering(String name, String text) {
		return new FakeProvider(name, true, d -> reply(text));
	}

	private static FakeProvider failing(String name) {
		return new FakeProvider(name, true, d -> {
			throw new IllegalStateException("HTTP 503");
		});
	}

	private static AiService chain(AiProvider... providers) {
		return new AiService(List.of(providers), new ObjectMapper());
	}

	private static final String GOOD = "{\"title\":\"DBMS assignment 3\",\"category\":\"ASSIGNMENT\",\"hours\":4}";

	@Test
	void primaryAnswerIsUsedAndFallbackNeverCalled() {
		FakeProvider gemini = answering("gemini", GOOD);
		FakeProvider groq = answering("groq", GOOD);

		TaskSuggestion s = chain(gemini, groq).suggest("finish dbms assignment 3, about 4 hours");

		assertThat(s).isEqualTo(new TaskSuggestion("DBMS assignment 3", Category.ASSIGNMENT, new BigDecimal("4.0")));
		assertThat(gemini.calls).containsExactly("finish dbms assignment 3, about 4 hours");
		assertThat(groq.calls).isEmpty();
	}

	@Test
	void eachFailureKindFallsThroughToTheNextProviderExactlyOnce() {
		for (FakeProvider broken : List.of(
				failing("gemini"),
				answering("gemini", "not json at all"),
				answering("gemini", "{\"title\":\"\",\"category\":\"LAB\",\"hours\":2}"),
				answering("gemini", "{\"title\":\"Lab\",\"category\":\"LAB\",\"hours\":0}"),
				answering("gemini", "{\"title\":\"Lab\",\"category\":\"LAB\",\"hours\":\"lots\"}"),
				new FakeProvider("gemini", true, d -> new AiReply("{\"title\":\"La", true, 70, 80, 0)))) {
			FakeProvider groq = answering("groq", GOOD);

			assertThat(chain(broken, groq).suggest("x").title()).isEqualTo("DBMS assignment 3");
			assertThat(broken.calls).as("no same-provider retry").hasSize(1);
			assertThat(groq.calls).hasSize(1);
		}
	}

	@Test
	void allProvidersFailingOrUnconfiguredMeansAiUnavailable() {
		FakeProvider unconfigured = new FakeProvider("gemini", false, d -> reply(GOOD));

		assertThatThrownBy(() -> chain(unconfigured, failing("groq")).suggest("x"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AI_UNAVAILABLE"));
		assertThat(unconfigured.calls).as("an unconfigured provider is skipped, not called").isEmpty();

		assertThatThrownBy(() -> chain().suggest("x"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AI_UNAVAILABLE"));
	}

	@Test
	void normalizationCleansWhatItCan() {
		AiService ai = chain();

		// code fence, extra spaces, lowercase category, hours rounded to one decimal
		assertThat(ai.normalize("```json\n{\"title\":\"  Lab   report \",\"category\":\"lab\",\"hours\":2.46}\n```"))
				.isEqualTo(new TaskSuggestion("Lab report", Category.LAB, new BigDecimal("2.5")));
		// out-of-vocabulary category is not a failure
		assertThat(ai.normalize("{\"title\":\"Revise\",\"category\":\"STUDY\",\"hours\":1}").category()).isEqualTo(Category.UNCATEGORIZED);
		// hours given as text, and clamped into the form's 0.1-999.9 range
		assertThat(ai.normalize("{\"title\":\"t\",\"category\":\"EXAM\",\"hours\":\"3\"}").estimatedHours()).isEqualByComparingTo("3");
		assertThat(ai.normalize("{\"title\":\"t\",\"category\":\"EXAM\",\"hours\":0.01}").estimatedHours()).isEqualByComparingTo("0.1");
		assertThat(ai.normalize("{\"title\":\"t\",\"category\":\"EXAM\",\"hours\":5000}").estimatedHours()).isEqualByComparingTo("999.9");
		// title capped at the column's 200 characters
		assertThat(ai.normalize("{\"title\":\"" + "a".repeat(250) + "\",\"category\":\"EXAM\",\"hours\":1}").title()).hasSize(200);
	}

}
