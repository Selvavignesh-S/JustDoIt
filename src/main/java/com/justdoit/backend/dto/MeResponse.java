package com.justdoit.backend.dto;

import com.justdoit.backend.entity.User;

public record MeResponse(String fullName, String username, String email, boolean emailVerified) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getFullName(), user.getUsername(), user.getEmail(), user.isEmailVerified());
	}

}
