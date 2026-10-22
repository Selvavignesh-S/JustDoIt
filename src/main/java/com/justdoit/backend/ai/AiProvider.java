package com.justdoit.backend.ai;

// One AI vendor behind the AI Service (SRS 8.4). Providers only speak HTTP; parsing and validation
// happen once, in AiService, so every provider is held to the same rules.
public interface AiProvider {

	String name();

	String model();

	// No API key configured: the chain skips this provider without calling it
	boolean isConfigured();

	// One attempt, no retries (SRS 8.6 rule 7). Throws on network/HTTP failure or timeout.
	AiReply complete(String description);

}
