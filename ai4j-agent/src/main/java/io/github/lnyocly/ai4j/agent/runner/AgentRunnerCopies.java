package io.github.lnyocly.ai4j.agent.runner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentRunnerCopies {

    private AgentRunnerCopies() {
    }

    static Map<String, String> copyStringMap(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry != null && entry.getKey() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return copy;
    }

    static Map<String, Object> copyObjectMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry != null && entry.getKey() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return copy;
    }

    static List<AgentRunnerEvent> copyEvents(List<AgentRunnerEvent> source) {
        List<AgentRunnerEvent> copy = new ArrayList<AgentRunnerEvent>();
        if (source != null) {
            for (AgentRunnerEvent event : source) {
                if (event != null) {
                    copy.add(event.copy());
                }
            }
        }
        return copy;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
