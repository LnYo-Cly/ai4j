package io.github.lnyocly.ai4j.coding.runtime;

import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.task.CodingTask;

public interface CodingRuntimeListener {

    void onTaskCreated(CodingTask task, CodingSessionLink link);

    void onTaskUpdated(CodingTask task);
}
