package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Identity and ownership data for one durable business-tool invocation. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessToolInvocationSpec {

    private String invocationId;
    private String executionId;
    private String taskId;
    private String sessionId;
    private String scopeKey;
    private String toolName;
    private String callId;
    private String arguments;
}
