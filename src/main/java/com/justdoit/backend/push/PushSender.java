package com.justdoit.backend.push;

import java.util.List;
import java.util.Set;

public interface PushSender {

	// Sends the messages; returns the tokens the push service says no longer exist (app uninstalled)
	Set<String> send(List<PushMessage> messages);

}
