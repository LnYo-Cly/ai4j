package io.github.lnyocly.ai4j.coding.runtime;

import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateRequest;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateResult;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.task.CodingTask;

import java.util.List;

public interface CodingRuntime {

    CodingDelegateResult delegate(CodingSession parentSession, CodingDelegateRequest request) throws Exception;

    void addListener(CodingRuntimeListener listener);

    void removeListener(CodingRuntimeListener listener);

    CodingTask getTask(String taskId);

    List<CodingTask> listTasks();

    List<CodingTask> listTasksByParentSessionId(String parentSessionId);

    List<CodingSessionLink> listSessionLinks(String parentSessionId);

    CodingAgentDefinitionRegistry getDefinitionRegistry();
}
