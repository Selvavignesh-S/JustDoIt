package com.justdoit.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// A browser's pre-flight check before a cross-origin call: only listed origins get a yes
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:8081")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void listedOriginIsAllowed() throws Exception {
		mvc.perform(options("/auth/login").header("Origin", "http://localhost:8081").header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));
	}

	@Test
	void anyOtherOriginIsRefused() throws Exception {
		mvc.perform(options("/auth/login").header("Origin", "https://evil.example").header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
