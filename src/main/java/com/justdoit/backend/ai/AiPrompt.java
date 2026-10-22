package com.justdoit.backend.ai;

import java.util.Arrays;
import java.util.List;

import com.justdoit.backend.entity.Category;

// The fixed request parts shared by every provider (SRS 8.6 rules 3-4). Identical on every call:
// the user's description is the only thing that varies.
public final class AiPrompt {

	// ~45 tokens. Category names are carried by the output schema (Gemini) or appended once (Groq JSON mode).
	public static final String INSTRUCTION = "Extract a task from the text. title: 8 words max. "
			+ "category: best fit from the enum, else UNCATEGORIZED. "
			+ "hours: estimated effort, 0.5 steps; use hours stated in the text if any. JSON only.";

	// Expected answer is ~35-40 tokens; a reply that hits this cap is treated as invalid (SRS 8.5)
	public static final int MAX_OUTPUT_TOKENS = 80;

	public static final List<String> CATEGORIES = Arrays.stream(Category.values()).map(Enum::name).toList();

	private AiPrompt() {
	}

}
