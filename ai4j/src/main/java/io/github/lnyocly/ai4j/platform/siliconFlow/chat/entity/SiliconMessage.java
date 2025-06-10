package io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SiliconMessage {
    private String role;
    private String content;
    @JsonProperty("reasoning_content")
    private String reasoningContent;
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
}
