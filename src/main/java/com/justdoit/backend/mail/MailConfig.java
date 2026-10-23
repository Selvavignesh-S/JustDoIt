package com.justdoit.backend.mail;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// Picks the email sender at startup and refuses to start on a missing, misspelled or unsafe setting.
// "console" prints verification and reset codes to the log, so a forgotten MAIL_PROVIDER on Render
// must never silently fall back to it: anyone with log access could then reset any password.
@Configuration
public class MailConfig {

	@Bean
	EmailService emailService(@Value("${mail.provider:}") String provider, @Value("${mail.api-key:}") String apiKey,
			@Value("${mail.from:}") String from, Environment env) {
		return switch (provider.strip().toLowerCase(Locale.ROOT)) {
			case "resend" -> new ResendEmailService(apiKey, from);
			case "none" -> new DisabledEmailService();
			case "console" -> {
				if (!env.matchesProfiles("local | test")) {
					throw new IllegalStateException("MAIL_PROVIDER=console prints reset codes to the log and is only allowed "
							+ "with the local or test profile. Use MAIL_PROVIDER=resend (or none) in production.");
				}
				yield new ConsoleEmailService();
			}
			default -> throw new IllegalStateException("MAIL_PROVIDER must be set to 'resend' (send email), 'none' "
					+ "(email off) or 'console' (local/test only), but was '" + provider + "'.");
		};
	}

}
