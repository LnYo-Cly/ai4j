package io.github.lnyocly.ai4j.agent.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.util.AgentJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AgentTeamPlanParser {

    private AgentTeamPlanParser() {
    }

    static List<AgentTeamTask> parseTasks(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<AgentTeamTask> tasks = parseFromJson(rawText);
        if (!tasks.isEmpty()) {
            return tasks;
        }

        String jsonPart = extractJson(rawText);
        if (jsonPart == null) {
            return Collections.emptyList();
        }
        return parseFromJson(jsonPart);
    }

    private static List<AgentTeamTask> parseFromJson(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            if (trimmed.startsWith("[")) {
                return parseArray(AgentJson.parseArrayStrictOrNull(trimmed));
            }
            if (trimmed.startsWith("{")) {
                ObjectNode obj = AgentJson.parseObjectStrictOrNull(trimmed);
                ArrayNode tasksArray = firstArray(obj, "tasks", "plan", "delegations", "assignments");
                if (tasksArray != null) {
                    return parseArray(tasksArray);
                }
                AgentTeamTask singleTask = parseTask(obj);
                if (singleTask == null) {
                    return Collections.emptyList();
                }
                List<AgentTeamTask> tasks = new ArrayList<>();
                tasks.add(singleTask);
                return tasks;
            }
            return Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static ArrayNode firstArray(ObjectNode obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = obj.get(key);
            if (value != null && value.isArray()) {
                return (ArrayNode) value;
            }
        }
        return null;
    }

    private static List<AgentTeamTask> parseArray(ArrayNode array) {
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTask> tasks = new ArrayList<>();
        for (JsonNode item : array) {
            if (item == null || !item.isObject()) {
                continue;
            }
            AgentTeamTask task = parseTask((ObjectNode) item);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private static AgentTeamTask parseTask(ObjectNode obj) {
        if (obj == null) {
            return null;
        }
        String taskId = firstString(obj, "id", "taskId", "task_id", "name");
        String memberId = firstString(obj, "memberId", "member", "agent", "assignee");
        String task = firstString(obj, "task", "instruction", "goal", "work", "input");
        String context = firstString(obj, "context", "notes", "memo", "details");
        List<String> dependsOn = parseDependencies(firstValue(obj, "dependsOn", "depends_on", "deps", "after"));
        if (task == null || task.trim().isEmpty()) {
            return null;
        }
        return AgentTeamTask.builder()
                .id(taskId)
                .memberId(memberId)
                .task(task.trim())
                .context(context == null ? null : context.trim())
                .dependsOn(dependsOn)
                .build();
    }

    private static List<String> parseDependencies(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return Collections.emptyList();
        }
        List<String> dependencies = new ArrayList<>();
        if (raw.isArray()) {
            for (JsonNode item : raw) {
                if (item != null && item.isTextual()) {
                    String value = item.asText().trim();
                    if (!value.isEmpty()) {
                        dependencies.add(value);
                    }
                }
            }
            return dependencies;
        }
        if (raw.isTextual()) {
            String[] parts = raw.asText().split(",");
            for (String part : parts) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    dependencies.add(value);
                }
            }
            return dependencies;
        }
        return Collections.emptyList();
    }

    private static JsonNode firstValue(ObjectNode obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (obj.has(key)) {
                return obj.get(key);
            }
        }
        return null;
    }

    private static String firstString(ObjectNode obj, String... keys) {
        JsonNode value = firstValue(obj, keys);
        if (value != null && value.isTextual()) {
            String text = value.asText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static String extractJson(String text) {
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        char open;
        char close;

        if (objectStart < 0 && arrayStart < 0) {
            return null;
        }
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            open = '[';
            close = ']';
        } else {
            start = objectStart;
            open = '{';
            close = '}';
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == open) {
                depth++;
                continue;
            }
            if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}