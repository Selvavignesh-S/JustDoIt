package com.justdoit.backend.config;

import java.util.HexFormat;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.exception.ApiError;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		// Missing, expired or tampered JWT -> the same JSON error shape as every other route
		AuthenticationEntryPoint unauthorized = (request, response, ex) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getOutputStream(), new ApiError("UNAUTHORIZED", "Sign in again to continue."));
		};

		http
				// Stateless JWT API (SRS 7.2): no session, so no CSRF token to protect
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Render health checks and cron-job.org keep-alive pings call this unauthenticated
						.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
						// Register, login, verify email and password reset happen before the user has a token
						.requestMatchers("/auth/**").permitAll()
						.anyRequest().authenticated())
				// Reads "Authorization: Bearer <jwt>", checks signature and expiry, and sets the current user
				.oauth2ResourceServer(oauth -> oauth
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(unauthorized))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized));
		return http.build();
	}

	@Bean
	SecretKey jwtSigningKey(@Value("${security.jwt.secret}") String secret) {
		byte[] bytes;
		try {
			bytes = HexFormat.of().parseHex(secret.trim());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("security.jwt.secret must be hex, e.g. the output of: openssl rand -hex 32", e);
		}
		// HS256 needs at least a 256-bit key
		if (bytes.length < 32) {
			throw new IllegalStateException("security.jwt.secret must be at least 64 hex characters (openssl rand -hex 32)");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
