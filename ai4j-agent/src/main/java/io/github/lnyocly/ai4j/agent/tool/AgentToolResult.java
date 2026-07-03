package io.github.lnyocly.ai4j.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {

    private String name;

    private String callId;

    private String output;

    /**
     * 可选：该 tool 执行产生的 sub-trace（如 RAG 检索的 {@code RagResult}，含 retrievedHits /
     * rerankedHits / citations），供 IoCapture 捕获到 TOOL 节点，使「tool 内部步骤」在 agent
     * trace 里可见。默认 null（普通 tool 不带）。LLM 只看 {@link #output}，不受影响。
     */
    private Object trace;
}
