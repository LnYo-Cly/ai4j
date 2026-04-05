package io.github.lnyocly.ai4j.coding.task;

import java.util.List;

public interface CodingTaskManager {

    CodingTask save(CodingTask task);

    CodingTask getTask(String taskId);

    List<CodingTask> listTasks();

    List<CodingTask> listTasksByParentSessionId(String parentSessionId);
}
