package com.justdoit.backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

// Prints raw codes to the log, which SRS 7.5 forbids in production: MailConfig only allows it with the local/test profile
public class ConsoleEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

	@Async
	@Override
	public void send(String to, String subject, String body) {
		log.info("\n--- EMAIL (console provider, not sent) ---\nTo: {}\nSubject: {}\n\n{}\n------------------------------------------", to, subject, body);
	}

}
