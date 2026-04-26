package io.github.lnyocly.ai4j.flowgram.springboot.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryFlowGramTaskStore implements FlowGramTaskStore {

    private final ConcurrentMap<String, FlowGramStoredTask> tasks = new ConcurrentHashMap<String, FlowGramStoredTask>();

    @Override
    public void save(FlowGramStoredTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().trim().isEmpty()) {
            return;
        }
        tasks.put(task.getTaskId(), copy(task));
    }

    @Override
    public FlowGramStoredTask find(String taskId) {
        FlowGramStoredTask task = tasks.get(taskId);
        return task == null ? null : copy(task);
    }

    @Override
    public void updateState(String taskId, String status, Boolean terminated, String error, Map<String, Object> resultSnapshot) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        tasks.compute(taskId, (key, existing) -> {
            FlowGramStoredTask target = existing == null
                    ? FlowGramStoredTask.builder().taskId(taskId).build()
                    : existing.toBuilder().build();
            if (status != null) {
                target.setStatus(status);
            }
            if (terminated != null) {
                target.setTerminated(terminated);
            }
            if (error != null || target.getError() != null) {
                target.setError(error);
            }
            if (resultSnapshot != null) {
                target.setResultSnapshot(copyMap(resultSnapshot));
            }
            return target;
        });
    }

    private FlowGramStoredTask copy(FlowGramStoredTask task) {
        return task.toBuilder()
                .resultSnapshot(copyMap(task.getResultSnapshot()))
                .build();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}
