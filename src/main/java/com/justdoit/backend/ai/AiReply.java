package com.justdoit.backend.ai;

// What one provider call returned, before any parsing. Token counts may be null if the provider omits them.
public record AiReply(String text, boolean truncated, Integer promptTokens, Integer outputTokens, Integer reasoningTokens) {
}
