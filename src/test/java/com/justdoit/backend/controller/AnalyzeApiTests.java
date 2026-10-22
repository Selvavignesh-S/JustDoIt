package com.justdoit.backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.ai.AiService;
import com.justdoit.backend.dto.TaskSuggestion;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.service.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalyzeApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private UserRepository users;

	@Autowired
	private JwtService jwtService;

	// Stands in for the provider chain, so no test ever calls a real AI API
	@MockitoBean
	private AiService ai;

	@Test
	void returnsTheSuggestionAndSendsOnlyTheCollapsedDescription() throws Exception {
		when(ai.suggest("Finish lab report, about 2 hours"))
				.thenReturn(new TaskSuggestion("Lab report", Category.LAB, new BigDecimal("2.0")));

		analyze(token("analyzer"), "  Finish   lab report,\n about 2 hours  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Lab report"))
				.andExpect(jsonPath("$.category").value("LAB"))
				.andExpect(jsonPath("$.estimatedHours").value(2.0));
	}

	@Test
	void descriptionOver500CharactersIsRejectedWithoutCallingTheAi() throws Exception {
		String token = token("verbose");
		analyze(token, "x".repeat(501))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.description").value("Keep the description under 500 characters."));
		// 500 characters once whitespace is collapsed is fine
		analyze(token, "x".repeat(250) + "        " + "y".repeat(249)).andExpect(status().isOk());
		analyze(token, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.description").exists());

		verify(ai, times(1)).suggest(anyString());
	}

	@Test
	void fiveAnalysesPerMinuteThenRateLimitedWithNoAiCall() throws Exception {
		String token = token("eager");
		for (int i = 0; i < 5; i++) {
			analyze(token, "task " + i).andExpect(status().isOk());
		}
		analyze(token, "task 6")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		verify(ai, times(5)).suggest(anyString());
		verify(ai, never()).suggest("task 6");
	}

	@Test
	void allProvidersFailingGivesTheDistinctAiUnavailableCode() throws Exception {
		when(ai.suggest(anyString())).thenThrow(ApiException.aiUnavailable());
		analyze(token("unlucky"), "anything")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
	}

	private String token(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Analyze Tester");
		user.setPasswordHash("not-used");
		return jwtService.issue(users.save(user));
	}

	private ResultActions analyze(String token, String description) throws Exception {
		return mvc.perform(post("/tasks/analyze")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("description", description))));
	}

}
