package com.justdoit.backend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

// The app must never start in production with an email sender that leaks reset codes or sends nothing
class MailConfigTests {

	private final MailConfig config = new MailConfig();

	private static MockEnvironment profiles(String... active) {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles(active);
		return env;
	}

	@Test
	void missingOrMisspelledProviderRefusesToStart() {
		assertThatThrownBy(() -> config.emailService("", "key", "a@b.dev", profiles()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_PROVIDER must be set");
		assertThatThrownBy(() -> config.emailService("sendgrid", "key", "a@b.dev", profiles()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("was 'sendgrid'");
	}

	@Test
	void consoleOnlyWithLocalOrTestProfile() {
		assertThatThrownBy(() -> config.emailService("console", "", "", profiles()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("only allowed with the local or test profile");
		assertThatThrownBy(() -> config.emailService("console", "", "", profiles("prod")))
				.isInstanceOf(IllegalStateException.class);

		assertThat(config.emailService("console", "", "", profiles("local"))).isInstanceOf(ConsoleEmailService.class);
		assertThat(config.emailService("Console", "", "", profiles("test"))).isInstanceOf(ConsoleEmailService.class);
	}

	@Test
	void noneTurnsEmailOffInAnyProfile() {
		EmailService off = config.emailService("none", "", "", profiles("prod"));
		assertThat(off).isInstanceOf(DisabledEmailService.class);
		assertThat(off.isDisabled()).isTrue();
		assertThat(config.emailService("console", "", "", profiles("local")).isDisabled()).isFalse();
	}

	@Test
	void resendNeedsKeyAndSender() {
		assertThatThrownBy(() -> config.emailService("resend", "", "noreply@example.dev", profiles()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("EMAIL_API_KEY");
		assertThatThrownBy(() -> config.emailService("resend", "re_key", "", profiles()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");

		assertThat(config.emailService("resend", "re_key", "noreply@example.dev", profiles())).isInstanceOf(ResendEmailService.class);
	}

}
