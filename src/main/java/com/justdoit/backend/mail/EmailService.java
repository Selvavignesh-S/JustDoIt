package com.justdoit.backend.mail;

// Picked by mail.provider: "console" (local dev, prints to the log) or "resend" (real email)
public interface EmailService {

	// Implementations send asynchronously, so a slow mail API never delays the HTTP response
	// (and response timing can't reveal whether an email was sent, SRS 7.5 step 2)
	void send(String to, String subject, String body);

}
