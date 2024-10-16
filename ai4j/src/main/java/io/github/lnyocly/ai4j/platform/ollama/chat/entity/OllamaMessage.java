package io.github.lnyocly.ai4j.platform.ollama.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/9/20 0:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaMessage {
    private String role;
    private String content;
    private List<String> images;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
}
