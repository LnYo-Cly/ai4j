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

    /**
     * Ollama Qwen 模型的思考内容字段
     * 该字段会在 Converter 层映射到 OpenAI 格式的 reasoning_content
     */
    private String thinking;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
}
