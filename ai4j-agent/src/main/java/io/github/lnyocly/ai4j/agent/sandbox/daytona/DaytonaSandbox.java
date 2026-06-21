package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal Daytona sandbox DTO used by the agent sandbox provider.
 */
public final class DaytonaSandbox {

    private String id;
    private String name;
    private String state;
    private String desiredState;
    private String snapshot;
    private String user;
    private String target;
    private String errorReason;
    private String toolboxProxyUrl;
    private Map<String, String> labels;

    public DaytonaSandbox() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDesiredState() {
        return desiredState;
    }

    public void setDesiredState(String desiredState) {
        this.desiredState = desiredState;
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public String getToolboxProxyUrl() {
        return toolboxProxyUrl;
    }

    public void setToolboxProxyUrl(String toolboxProxyUrl) {
        this.toolboxProxyUrl = toolboxProxyUrl;
    }

    public Map<String, String> getLabels() {
        return copyLabels(labels);
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = copyLabels(labels);
    }

    public DaytonaSandbox copy() {
        DaytonaSandbox copy = new DaytonaSandbox();
        copy.setId(id);
        copy.setName(name);
        copy.setState(state);
        copy.setDesiredState(desiredState);
        copy.setSnapshot(snapshot);
        copy.setUser(user);
        copy.setTarget(target);
        copy.setErrorReason(errorReason);
        copy.setToolboxProxyUrl(toolboxProxyUrl);
        copy.setLabels(labels);
        return copy;
    }

    private static Map<String, String> copyLabels(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }
}
