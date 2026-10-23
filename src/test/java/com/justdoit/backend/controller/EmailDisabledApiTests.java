package com.justdoit.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.repository.EmailVerificationTokenRepository;

// MAIL_PROVIDER=none: emails are collected and kept unconfirmed, nothing is sent, reset says "not yet"
@SpringBootTest(properties = "mail.provider=none")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailDisabledApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private EmailVerificationTokenRepository verificationTokens;

	@Test
	void emailIsSavedButNothingIsSentAndResetSaysNotYet() throws Exception {
		long codesBefore = verificationTokens.count();
		send("/auth/register", "{\"fullName\":\"No Mail\",\"username\":\"no_mail\",\"password\":\"Password123\",\"email\":\"NoMail@Example.com\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("nomail@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(false));
		assertThat(verificationTokens.count()).as("no confirmation code created").isEqualTo(codesBefore);

		// Same answer for a known and an unknown address, so it reveals nothing about accounts
		send("/auth/password-reset/request", "{\"email\":\"nomail@example.com\"}")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("EMAIL_DISABLED"));
		send("/auth/password-reset/request", "{\"email\":\"nobody@example.com\"}")
				.andExpect(jsonPath("$.code").value("EMAIL_DISABLED"));

		String token = json.readTree(send("/auth/login", "{\"username\":\"no_mail\",\"password\":\"Password123\"}")
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

		// Changing the email still works and stays unconfirmed; asking for a link says "not yet"
		mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"second@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("second@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(false));
		mvc.perform(post("/users/me/resend-verification").header("Authorization", "Bearer " + token))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("EMAIL_DISABLED"));
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.email").value("second@example.com"));
		assertThat(verificationTokens.count()).isEqualTo(codesBefore);
	}

	private ResultActions send(String path, String body) throws Exception {
		return mvc.perform(post(path)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
