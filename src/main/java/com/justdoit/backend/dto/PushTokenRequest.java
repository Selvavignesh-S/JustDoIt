package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// An Expo push token, e.g. ExponentPushToken[xxxxxxxx]: only that shape is stored or sent anywhere
public record PushTokenRequest(
		@NotBlank(message = "Missing push token.") @Size(max = 200, message = "Push token is too long.")
		@Pattern(regexp = "Expo(nent)?PushToken\\[[A-Za-z0-9_-]+\\]", message = "Not a push token.") String token) {
}
