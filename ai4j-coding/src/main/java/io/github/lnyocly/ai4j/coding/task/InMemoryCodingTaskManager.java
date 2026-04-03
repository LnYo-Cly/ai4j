package io.github.lnyocly.ai4j.coding.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCodingTaskManager implements CodingTaskManager {

    private final Map<String, CodingTask> tasks = new ConcurrentHashMap<String, CodingTask>();

    @Override
    public CodingTask save(CodingTask task) {
        if (task == null || isBlank(task.getTaskId())) {
            throw new IllegalArgumentException("taskId is required");
        }
        CodingTask stored = task.toBuilder().build();
        tasks.put(stored.getTaskId(), stored);
        return stored.toBuilder().build();
    }

    @Override
    public CodingTask getTask(String taskId) {
        CodingTask task = taskId == null ? null : tasks.get(taskId);
        return task == null ? null : task.toBuilder().build();
    }

    @Override
    public List<CodingTask> listTasks() {
        return sort(tasks.values());
    }

    @Override
    public List<CodingTask> listTasksByParentSessionId(String parentSessionId) {
        if (isBlank(parentSessionId)) {
            return Collections.emptyList();
        }
        List<CodingTask> matches = new ArrayList<CodingTask>();
        for (CodingTask task : tasks.values()) {
            if (task != null && parentSessionId.equals(task.getParentSessionId())) {
                matches.add(task.toBuilder().build());
            }
        }
        sortInPlace(matches);
        return matches;
    }

    private List<CodingTask> sort(Iterable<CodingTask> values) {
        List<CodingTask> items = new ArrayList<CodingTask>();
        for (CodingTask task : values) {
            if (task != null) {
                items.add(task.toBuilder().build());
            }
        }
        sortInPlace(items);
        return items;
    }

    private void sortInPlace(List<CodingTask> items) {
        Collections.sort(items, new Comparator<CodingTask>() {
            @Override
            public int compare(CodingTask left, CodingTask right) {
                long leftTime = left == null ? 0L : left.getCreatedAtEpochMs();
                long rightTime = right == null ? 0L : right.getCreatedAtEpochMs();
                if (leftTime == rightTime) {
                    String leftId = left == null ? null : left.getTaskId();
                    String rightId = right == null ? null : right.getTaskId();
                    if (leftId == null) {
                        return rightId == null ? 0 : -1;
                    }
                    if (rightId == null) {
                        return 1;
                    }
                    return leftId.compareTo(rightId);
                }
                return leftTime < rightTime ? -1 : 1;
            }
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
