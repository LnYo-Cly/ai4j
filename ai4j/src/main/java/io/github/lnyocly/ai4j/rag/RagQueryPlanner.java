package io.github.lnyocly.ai4j.rag;

/**
 * Plans retrieval queries before the retriever runs.
 *
 * <p>The planner is RAG-scoped: it does not route tools or agents. It can
 * return one rewritten query or multiple retrieval variants such as
 * multi-query expansion, HyDE, or step-back queries.</p>
 */
public interface RagQueryPlanner {

    RagQueryPlan plan(RagQuery query) throws Exception;
}
