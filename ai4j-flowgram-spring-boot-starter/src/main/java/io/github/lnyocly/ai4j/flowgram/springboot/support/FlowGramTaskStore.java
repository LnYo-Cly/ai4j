package io.github.lnyocly.ai4j.flowgram.springboot.support;

import java.util.Map;

public interface FlowGramTaskStore {

    void save(FlowGramStoredTask task);

    FlowGramStoredTask find(String taskId);

    void updateState(String taskId, String status, Boolean terminated, String error, Map<String, Object> resultSnapshot);
}
