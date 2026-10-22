package com.justdoit.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTests {

	// A clock the test can move forward by hand
	private static final class MovableClock extends Clock {
		private Instant now = Instant.parse("2026-10-22T10:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	@Test
	void allowsUpToTheLimitThenBlocksUntilTheWindowEnds() {
		MovableClock clock = new MovableClock();
		RateLimiter limiter = new RateLimiter(clock);
		Duration hour = Duration.ofHours(1);

		assertThat(limiter.tryAcquire("k", 3, hour)).isTrue();
		assertThat(limiter.tryAcquire("k", 3, hour)).isTrue();
		assertThat(limiter.tryAcquire("k", 3, hour)).isTrue();
		assertThat(limiter.tryAcquire("k", 3, hour)).isFalse();
		assertThat(limiter.tryAcquire("other", 3, hour)).as("keys are independent").isTrue();

		clock.now = clock.now.plus(Duration.ofMinutes(59));
		assertThat(limiter.tryAcquire("k", 3, hour)).as("still inside the window").isFalse();

		clock.now = clock.now.plus(Duration.ofMinutes(1));
		assertThat(limiter.tryAcquire("k", 3, hour)).as("new window").isTrue();
	}

	@Test
	void evictionDropsOnlyExpiredWindows() {
		MovableClock clock = new MovableClock();
		RateLimiter limiter = new RateLimiter(clock);
		limiter.tryAcquire("short", 1, Duration.ofMinutes(1));
		limiter.tryAcquire("long", 1, Duration.ofHours(1));

		clock.now = clock.now.plus(Duration.ofMinutes(5));
		limiter.evictExpired();

		assertThat(limiter.tryAcquire("short", 1, Duration.ofMinutes(1))).isTrue();
		assertThat(limiter.tryAcquire("long", 1, Duration.ofHours(1))).isFalse();
	}

}
