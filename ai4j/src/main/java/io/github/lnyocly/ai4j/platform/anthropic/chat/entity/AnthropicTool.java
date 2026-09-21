package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic Messages 请求中的工具定义。
 * <p>
 * 对应 OpenAI {@code Tool.Function}，但 schema 字段名为 {@code input_schema}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicTool {

    private String name;

    /** Server tool 类型（如 {@code tool_search_tool_bm25_20251119}、{@code web_search_20250305}）；普通 function 工具留空。 */
    private String type;

    private String description;

    @JsonProperty("input_schema")
    private Object inputSchema;

    @JsonProperty("cache_control")
    private Object cacheControl;

    /** Anthropic tool-search：标 {@code defer_loading=true} 的工具不进模型可见上下文，由 tool_search 按需加载。 */
    @JsonProperty("defer_loading")
    private Boolean deferLoading;

    /** Compatibility constructor retained for the original tool definition. */
    public AnthropicTool(String name, String description, Object inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
}
