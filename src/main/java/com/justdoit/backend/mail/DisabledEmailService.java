package com.justdoit.backend.mail;

// MAIL_PROVIDER=none: no domain yet, so nothing is ever sent. Emails are still collected (unconfirmed);
// switching to "resend" later lets users confirm them with "Resend link".
public class DisabledEmailService implements EmailService {

	@Override
	public boolean isDisabled() {
		return true;
	}

	// Callers check isDisabled() first; reaching this is a bug, not a user error
	@Override
	public void send(String to, String subject, String body) {
		throw new IllegalStateException("Email is disabled (MAIL_PROVIDER=none); nothing should try to send");
	}

}
