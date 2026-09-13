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

    /**
     * 显式成功/失败（#264）。{@code null} = 兼容旧调用方，按 {@link #output} 启发式判断；
     * {@code Boolean.FALSE} = 失败；{@code Boolean.TRUE} = 成功。
     */
    private Boolean ok;

    /** 失败原因（给人/trace 看）；LLM 仍主要读 {@link #output}。 */
    private String error;

    /** Structured lifecycle state for asynchronous or recoverable tools. */
    private AgentToolExecutionStatus status;

    /** Stable operation identity returned by a long-running tool. */
    private String operationId;

    /** Harness or host wait identity associated with {@link #operationId}. */
    private String waitId;

    /** Optional retry hint supplied by the tool or host. */
    private Long retryAfterMillis;

    /**
     * Source-compatible constructor retained for callers using the original
     * result shape before asynchronous lifecycle fields were added.
     */
    public AgentToolResult(String name,
                           String callId,
                           String output,
                           Object trace,
                           Boolean ok,
                           String error) {
        this.name = name;
        this.callId = callId;
        this.output = output;
        this.trace = trace;
        this.ok = ok;
        this.error = error;
    }

    /**
     * 是否应记为 TOOL span ERROR。
     * <p>规则：{@code ok == false}，或 {@code error} 非空，或 {@code output} 以 {@code TOOL_ERROR} 开头。
     */
    public boolean isFailed() {
        if (AgentToolExecutionStatus.FAILED.equals(status)
                || AgentToolExecutionStatus.UNKNOWN.equals(status)) {
            return true;
        }
        if (Boolean.FALSE.equals(ok)) {
            return true;
        }
        if (error != null && !error.trim().isEmpty()) {
            return true;
        }
        return output != null && output.startsWith("TOOL_ERROR");
    }

    public boolean isWaiting() {
        return AgentToolExecutionStatus.WAITING.equals(status);
    }
}
