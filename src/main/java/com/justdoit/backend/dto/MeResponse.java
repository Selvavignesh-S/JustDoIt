package com.justdoit.backend.dto;

import com.justdoit.backend.entity.User;

// emailEnabled: whether this server sends emails at all (MAIL_PROVIDER=none turns it off), so the app can
// say "we'll send a link" only when one will really be sent
public record MeResponse(String fullName, String username, String email, boolean emailVerified, boolean emailEnabled) {

	public static MeResponse from(User user, boolean emailEnabled) {
		return new MeResponse(user.getFullName(), user.getUsername(), user.getEmail(), user.isEmailVerified(), emailEnabled);
	}

}
