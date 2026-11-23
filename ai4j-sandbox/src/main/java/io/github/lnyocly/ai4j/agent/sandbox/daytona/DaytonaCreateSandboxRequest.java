package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daytona create sandbox request.
 */
public final class DaytonaCreateSandboxRequest {

    private String name;
    private String snapshot;
    private String user;
    private Map<String, String> env;
    private Map<String, String> labels;
    private String target;
    private Map<String, Object> options;

    public DaytonaCreateSandboxRequest() {
        this.env = new LinkedHashMap<String, String>();
        this.labels = new LinkedHashMap<String, String>();
        this.options = new LinkedHashMap<String, Object>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Map<String, String> getEnv() {
        return new LinkedHashMap<String, String>(env);
    }

    public void setEnv(Map<String, String> env) {
        this.env = new LinkedHashMap<String, String>();
        if (env != null) {
            this.env.putAll(env);
        }
    }

    public Map<String, String> getLabels() {
        return new LinkedHashMap<String, String>(labels);
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = new LinkedHashMap<String, String>();
        if (labels != null) {
            this.labels.putAll(labels);
        }
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Map<String, Object> getOptions() {
        return new LinkedHashMap<String, Object>(options);
    }

    public void setOptions(Map<String, Object> options) {
        this.options = new LinkedHashMap<String, Object>();
        if (options != null) {
            this.options.putAll(options);
        }
    }

    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        putIfNotNull(payload, "name", name);
        putIfNotNull(payload, "snapshot", snapshot);
        putIfNotNull(payload, "user", user);
        if (env != null && !env.isEmpty()) {
            payload.put("env", new LinkedHashMap<String, String>(env));
        }
        if (labels != null && !labels.isEmpty()) {
            payload.put("labels", new LinkedHashMap<String, String>(labels));
        }
        putIfNotNull(payload, "target", target);
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    payload.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return payload;
    }

    private static void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
