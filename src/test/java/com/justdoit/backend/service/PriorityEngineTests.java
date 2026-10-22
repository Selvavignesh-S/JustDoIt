package com.justdoit.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;

class PriorityEngineTests {

	private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

	private static Priority current(Category category, String hours, Duration untilDeadline) {
		return PriorityEngine.current(category, new BigDecimal(hours), NOW.plus(untilDeadline), NOW);
	}

	@Test
	void ratioThresholds() {
		// 10 h of work: HIGH under 30 h left, MEDIUM under 80 h, LOW from 80 h
		assertThat(current(Category.ASSIGNMENT, "10", Duration.ofHours(29))).isEqualTo(Priority.HIGH);
		assertThat(current(Category.ASSIGNMENT, "10", Duration.ofHours(30))).isEqualTo(Priority.MEDIUM);
		assertThat(current(Category.ASSIGNMENT, "10", Duration.ofHours(79))).isEqualTo(Priority.MEDIUM);
		assertThat(current(Category.ASSIGNMENT, "10", Duration.ofHours(80))).isEqualTo(Priority.LOW);
	}

	@Test
	void highStakesCategoriesMoveUpOneLevelButNotPastHigh() {
		assertThat(current(Category.EXAM, "10", Duration.ofHours(80))).isEqualTo(Priority.MEDIUM);
		assertThat(current(Category.PROJECT, "10", Duration.ofHours(40))).isEqualTo(Priority.HIGH);
		assertThat(current(Category.PRESENTATION, "10", Duration.ofHours(29))).isEqualTo(Priority.HIGH);
		assertThat(current(Category.PERSONAL, "10", Duration.ofHours(80))).isEqualTo(Priority.LOW);
	}

	@Test
	void underTwentyFourHoursIsDueTomorrowIncludingOverdue() {
		assertThat(current(Category.PERSONAL, "0.5", Duration.ofHours(23))).isEqualTo(Priority.DUE_TOMORROW);
		assertThat(current(Category.PERSONAL, "0.5", Duration.ofHours(-5))).isEqualTo(Priority.DUE_TOMORROW);
		assertThat(current(Category.PERSONAL, "0.5", Duration.ofHours(24))).isEqualTo(Priority.LOW);
	}

}
