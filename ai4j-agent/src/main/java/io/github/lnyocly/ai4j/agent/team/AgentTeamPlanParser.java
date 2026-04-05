package io.github.lnyocly.ai4j.agent.team;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

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
                return parseArray(JSON.parseArray(trimmed));
            }
            if (trimmed.startsWith("{")) {
                JSONObject obj = JSON.parseObject(trimmed);
                JSONArray tasksArray = firstArray(obj, "tasks", "plan", "delegations", "assignments");
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

    private static JSONArray firstArray(JSONObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = obj.get(key);
            if (value instanceof JSONArray) {
                return (JSONArray) value;
            }
        }
        return null;
    }

    private static List<AgentTeamTask> parseArray(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTask> tasks = new ArrayList<>();
        for (Object item : array) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            AgentTeamTask task = parseTask((JSONObject) item);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private static AgentTeamTask parseTask(JSONObject obj) {
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

    private static List<String> parseDependencies(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<String> dependencies = new ArrayList<>();
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (Object item : array) {
                if (item instanceof String) {
                    String value = ((String) item).trim();
                    if (!value.isEmpty()) {
                        dependencies.add(value);
                    }
                }
            }
            return dependencies;
        }
        if (raw instanceof String) {
            String[] parts = ((String) raw).split(",");
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

    private static Object firstValue(JSONObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (obj.containsKey(key)) {
                return obj.get(key);
            }
        }
        return null;
    }

    private static String firstString(JSONObject obj, String... keys) {
        Object value = firstValue(obj, keys);
        if (value instanceof String) {
            String text = ((String) value).trim();
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