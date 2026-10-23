package com.justdoit.backend.mail;

// Chosen by MailConfig from MAIL_PROVIDER: "resend" (real email), "none" (email off) or "console" (local/test only)
public interface EmailService {

	// true only for MAIL_PROVIDER=none: verification and reset emails can't be sent
	default boolean isDisabled() {
		return false;
	}

	// Implementations send asynchronously, so a slow mail API never delays the HTTP response
	// (and response timing can't reveal whether an email was sent, SRS 7.5 step 2)
	void send(String to, String subject, String body);

}
