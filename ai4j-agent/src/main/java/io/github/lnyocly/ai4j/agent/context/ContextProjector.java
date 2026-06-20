package io.github.lnyocly.ai4j.agent.context;

import java.util.List;

public interface ContextProjector {

    ContextProjection project(List<Object> items, ContextBudget budget);
}
