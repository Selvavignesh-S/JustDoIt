package com.justdoit.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.mail.EmailService;

// End-to-end through HTTP, Spring Security, services and the real Postgres schema.
// @Transactional rolls every test back, so the local database is left untouched.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTests {

	private static final Pattern CODE_IN_LINK = Pattern.compile("code=([A-Z0-9]{12})");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	// Replaces the real sender so tests can read the code that would have been emailed
	@MockitoBean
	private EmailService email;

	@Test
	void registerThenLoginIsCaseInsensitiveAndMeShowsProfile() throws Exception {
		register("Selva_V", "Password123", "Selva@Example.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("selva_v"))
				.andExpect(jsonPath("$.email").value("selva@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.token").doesNotExist());

		String token = login("SELVA_v", "Password123");
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Test User"))
				.andExpect(jsonPath("$.username").value("selva_v"));
	}

	@Test
	void duplicateUsernameIgnoresCaseAndReportsTheField() throws Exception {
		register("dupe_user", "Password123", null).andExpect(status().isCreated());
		register("Dupe_User", "Password123", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"))
				.andExpect(jsonPath("$.fieldErrors.username").value("That username is taken."));
	}

	@Test
	void fieldRulesAreEnforced() throws Exception {
		register("ab", "short", "not-an-email")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.username").exists())
				.andExpect(jsonPath("$.fieldErrors.password").value("Use at least 8 characters."))
				.andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email."));

		// 19 emoji = 76 bytes in UTF-8: fine by character count, too long for BCrypt
		register("emoji_user", "😀".repeat(19), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password").value("That password is too long."));
	}

	@Test
	void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
		register("real_user", "Password123", null).andExpect(status().isCreated());

		String wrongPassword = attemptLogin("real_user", "WrongPass99")
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		String unknownUser = attemptLogin("ghost_user", "WrongPass99")
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

		assertThat(unknownUser).isEqualTo(wrongPassword).contains("BAD_CREDENTIALS");
	}

	@Test
	void emailVerificationCodeWorksOnce() throws Exception {
		register("verify_me", "Password123", "verify@example.com").andExpect(status().isCreated());
		String code = lastCodeSentTo("verify@example.com");

		// Users may paste the grouped, lowercase form
		String pasted = (code.substring(0, 4) + " " + code.substring(4, 8) + "-" + code.substring(8)).toLowerCase();
		postJson("/auth/verify-email", "{\"code\":\"" + pasted + "\"}").andExpect(status().isNoContent());

		String token = login("verify_me", "Password123");
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.emailVerified").value(true));

		postJson("/auth/verify-email", "{\"code\":\"" + code + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void changingEmailResetsVerificationAndVoidsTheOldCode() throws Exception {
		register("changer", "Password123", "first@example.com").andExpect(status().isCreated());
		String oldCode = lastCodeSentTo("first@example.com");
		String token = login("changer", "Password123");

		mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Second@Example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("second@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(false));

		postJson("/auth/verify-email", "{\"code\":\"" + oldCode + "\"}")
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		postJson("/auth/verify-email", "{\"code\":\"" + lastCodeSentTo("second@example.com") + "\"}")
				.andExpect(status().isNoContent());
	}

	@Test
	void passwordResetFlow() throws Exception {
		register("forgetful", "OldPassword1", "forgetful@example.com").andExpect(status().isCreated());
		postJson("/auth/verify-email", "{\"code\":\"" + lastCodeSentTo("forgetful@example.com") + "\"}");

		String requested = requestReset("Forgetful@Example.com", "10.0.0.1")
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		String code = lastCodeSentTo("forgetful@example.com");

		postJson("/auth/password-reset/confirm", "{\"code\":\"" + code + "\",\"newPassword\":\"NewPassword2\"}")
				.andExpect(status().isNoContent());

		attemptLogin("forgetful", "OldPassword1").andExpect(status().isUnauthorized());
		login("forgetful", "NewPassword2");

		postJson("/auth/password-reset/confirm", "{\"code\":\"" + code + "\",\"newPassword\":\"Another333\"}")
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

		// Unknown email: identical response, and nothing is sent
		String unknown = requestReset("nobody@example.com", "10.0.0.1")
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		assertThat(unknown).isEqualTo(requested);
		verify(email, never()).send(eq("nobody@example.com"), anyString(), anyString());
	}

	@Test
	void unverifiedEmailCannotResetPassword() throws Exception {
		register("unverified", "Password123", "unverified@example.com").andExpect(status().isCreated());
		requestReset("unverified@example.com", "10.0.0.2").andExpect(status().isAccepted());
		// Only the verification email went out, never a reset email
		verify(email, never()).send(eq("unverified@example.com"), eq("Reset your JustDoIt password"), anyString());
	}

	@Test
	void passwordResetRequestsAreRateLimitedPerEmail() throws Exception {
		for (int i = 0; i < 3; i++) {
			requestReset("limited@example.com", "10.0.0.3").andExpect(status().isAccepted());
		}
		requestReset("limited@example.com", "10.0.0.3")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	private ResultActions register(String username, String password, String emailAddress) throws Exception {
		Map<String, String> body = new HashMap<>(); // HashMap, not Map.of, because email may be null
		body.put("fullName", "Test User");
		body.put("username", username);
		body.put("password", password);
		body.put("email", emailAddress);
		return postJson("/auth/register", json.writeValueAsString(body));
	}

	private ResultActions attemptLogin(String username, String password) throws Exception {
		return postJson("/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
	}

	private String login(String username, String password) throws Exception {
		String body = attemptLogin(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode node = json.readTree(body);
		return node.get("token").asText();
	}

	private ResultActions requestReset(String emailAddress, String ip) throws Exception {
		return mvc.perform(post("/auth/password-reset/request")
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + emailAddress + "\"}"));
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String lastCodeSentTo(String address) {
		ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
		verify(email, atLeastOnce()).send(eq(address), anyString(), bodies.capture());
		Matcher m = CODE_IN_LINK.matcher(bodies.getValue());
		assertThat(m.find()).as("email body contains a code link").isTrue();
		return m.group(1);
	}

}
