package com.justdoit.backend.mail;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

// Sends through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email).
// mail.from must be on a domain verified in Resend, or Resend only delivers to the account owner.
@Service
@ConditionalOnProperty(name = "mail.provider", havingValue = "resend")
public class ResendEmailService implements EmailService {

	private final RestClient client;
	private final String from;

	public ResendEmailService(@Value("${mail.api-key}") String apiKey, @Value("${mail.from}") String from) {
		if (apiKey.isBlank()) {
			throw new IllegalStateException("mail.provider=resend needs mail.api-key (EMAIL_API_KEY)");
		}
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(5_000);
		timeouts.setReadTimeout(10_000);
		this.client = RestClient.builder()
				.baseUrl("https://api.resend.com")
				.requestFactory(timeouts)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
		this.from = from;
	}

	// Failures are logged by Spring's async error handler; the user can tap "Resend link" or request another reset
	@Async
	@Override
	public void send(String to, String subject, String body) {
		client.post()
				.uri("/emails")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("from", from, "to", List.of(to), "subject", subject, "text", body))
				.retrieve()
				.toBodilessEntity();
	}

}
