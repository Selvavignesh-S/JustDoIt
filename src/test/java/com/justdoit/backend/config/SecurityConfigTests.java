package com.justdoit.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	@Test
	void everythingElseRequiresAuth() throws Exception {
		mockMvc.perform(get("/tasks")).andExpect(status().isForbidden());
	}

}
