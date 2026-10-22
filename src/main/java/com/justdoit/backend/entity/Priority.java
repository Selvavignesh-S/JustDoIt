package com.justdoit.backend.entity;

// Declared lowest to highest so ordinal() gives urgency order; the DB stores the name, not the ordinal
public enum Priority {
	LOW, MEDIUM, HIGH, DUE_TOMORROW
}
