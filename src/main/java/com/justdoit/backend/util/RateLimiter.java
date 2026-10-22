package com.justdoit.backend.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Fixed-window counter per key, e.g. "reset:email:a@b.com" -> 3 requests until 14:05.
// shortcut: counters live in this JVM's memory, so they reset on restart and aren't shared;
// fine for one Render instance, move to a shared store (e.g. Bucket4j + Postgres) before running two.
@Component
public class RateLimiter {

	private record Window(Instant resetAt, int count) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;

	public RateLimiter() {
		this(Clock.systemUTC());
	}

	RateLimiter(Clock clock) {
		this.clock = clock;
	}

	// true = allowed. compute() is atomic per key, so two simultaneous requests can't both take the last slot.
	public boolean tryAcquire(String key, int limit, Duration window) {
		Instant now = clock.instant();
		Window updated = windows.compute(key, (k, current) -> current == null || !now.isBefore(current.resetAt())
				? new Window(now.plus(window), 1)
				: new Window(current.resetAt(), current.count() + 1));
		return updated.count() <= limit;
	}

	// Without this, every distinct email/IP ever seen would stay in memory
	@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
	void evictExpired() {
		Instant now = clock.instant();
		windows.values().removeIf(w -> !now.isBefore(w.resetAt()));
	}

}
