package io.github.lnyocly.ai4j.platform.openai.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/13 2:07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {
    private String id;
    private String type;
    private Function function;

    /**
     * Position of this call in a streamed parallel tool-call response. OpenAI-compatible
     * providers commonly omit the id on continuation chunks but keep the index stable.
     */
    // Provider stream indexes are useful while parsing a response, but are not
    // part of an assistant tool-call request sent back to the provider.
    @JsonProperty(value = "index", access = JsonProperty.Access.WRITE_ONLY)
    private Integer index;

    /** Source-compatible constructor retained for callers using the original shape. */
    public ToolCall(String id, String type, Function function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private String name;
        private String arguments;
    }
}
