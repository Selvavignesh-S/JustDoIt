package com.justdoit.backend.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.justdoit.backend.entity.User;

@Service
public class JwtService {

	private final JwtEncoder encoder;
	private final Duration lifetime;

	public JwtService(JwtEncoder encoder, @Value("${security.jwt.expiration-ms}") long expirationMs) {
		this.encoder = encoder;
		this.lifetime = Duration.ofMillis(expirationMs);
	}

	// The token carries only the user id; everything else is looked up fresh from the database
	public String issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(lifetime))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
