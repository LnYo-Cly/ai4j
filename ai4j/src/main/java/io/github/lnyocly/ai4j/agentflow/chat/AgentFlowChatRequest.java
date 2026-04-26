package io.github.lnyocly.ai4j.agentflow.chat;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Collections;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowChatRequest {

    @NonNull
    private String prompt;

    @Builder.Default
    private Map<String, Object> inputs = Collections.emptyMap();

    private String userId;

    private String conversationId;

    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();

    @Builder.Default
    private Map<String, Object> extraBody = Collections.emptyMap();
}
