package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 请求体（对应 {@code POST /v1/messages}）。
 * <p>
 * 由统一的 {@code ChatCompletion}（OpenAI 格式）转换而来：
 * system 消息抽到顶层 {@link #system}，其余进入 {@link #messages}。
 * <p>
 * {@code extraBody} 用于透传协议扩展（如 {@code thinking}），序列化时展开到 JSON 顶层。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicChatCompletion {

    private String model;

    /** 系统提示，可以是字符串或 content block 数组；这里用 Object 承载 */
    private Object system;

    private List<AnthropicMessage> messages;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    private List<AnthropicTool> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    private Boolean stream;

    /** 额外请求体参数（如 thinking），序列化时展开到顶层 */
    @JsonIgnore
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public List<AnthropicMessage> safeMessages() {
        if (messages == null) {
            return new ArrayList<AnthropicMessage>();
        }
        return messages;
    }
}
