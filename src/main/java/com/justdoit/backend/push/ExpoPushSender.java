package com.justdoit.backend.push;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

// Expo's push service: one HTTP call for up to 100 messages; Expo hands them to Firebase (Android) or APNs (iOS).
// shortcut: reads only the immediate "tickets", not the delivery receipts Expo offers 15 min later; add a receipt
// check if pushes are found to go missing.
@Component
public class ExpoPushSender implements PushSender {

	private static final int BATCH = 100;

	private final String accessToken;
	private final RestClient client;

	public ExpoPushSender(@Value("${push.expo.access-token}") String accessToken) {
		this.accessToken = accessToken;
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(5_000);
		timeouts.setReadTimeout(15_000);
		this.client = RestClient.builder().baseUrl("https://exp.host/--/api/v2/push").requestFactory(timeouts).build();
	}

	@Override
	public Set<String> send(List<PushMessage> messages) {
		Set<String> gone = new HashSet<>();
		for (int from = 0; from < messages.size(); from += BATCH) {
			List<PushMessage> batch = messages.subList(from, Math.min(from + BATCH, messages.size()));
			List<Map<String, Object>> body = new ArrayList<>();
			for (PushMessage m : batch) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("to", m.to());
				item.put("title", m.title());
				item.put("body", m.body());
				item.put("data", m.data());
				item.put("sound", "default");
				item.put("priority", "high");
				// The Android channel the app creates; users can mute reminders separately in system settings
				item.put("channelId", "reminders");
				body.add(item);
			}
			RestClient.RequestBodySpec request = client.post().uri("/send").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
			if (!accessToken.isBlank()) {
				request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			}
			JsonNode tickets = request.body(body).retrieve().body(JsonNode.class).path("data");
			// Tickets come back in the same order as the messages
			for (int i = 0; i < batch.size() && i < tickets.size(); i++) {
				JsonNode ticket = tickets.get(i);
				if ("error".equals(ticket.path("status").asText()) && "DeviceNotRegistered".equals(ticket.path("details").path("error").asText())) {
					gone.add(batch.get(i).to());
				}
			}
		}
		return gone;
	}

}
