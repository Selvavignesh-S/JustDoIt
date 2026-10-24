package com.justdoit.backend.push;

import java.util.Map;

// One notification to one phone. data travels with it; the app reads taskId to open the task.
public record PushMessage(String to, String title, String body, Map<String, Object> data) {
}
