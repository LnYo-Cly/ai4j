package io.github.lnyocly.ai4j.platform.lingyi.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import lombok.*;

import java.util.List;

/**
 * @Author cly
 * @Description 零一万物对话请求实体
 * @Date 2024/9/9 23:01
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LingyiChatCompletion {

    @NonNull
    private String model;

    @NonNull
    private List<ChatMessage> messages;

    /**
     * 模型可能会调用的 tool 的列表。目前，仅支持 function 作为工具。使用此参数来提供以 JSON 作为输入参数的 function 列表。
     */
    private List<Tool> tools;

    /**
     * 辅助属性
     */
    @JsonIgnore
    private List<String> functions;

    /**
     * 控制模型调用 tool 的行为。
     * none 意味着模型不会调用任何 tool，而是生成一条消息。
     * auto 意味着模型可以选择生成一条消息或调用一个或多个 tool。
     * 当没有 tool 时，默认值为 none。如果有 tool 存在，默认值为 auto。
     */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /**
     * 采样温度，介于 0 和 2 之间。更高的值，如 0.8，会使输出更随机，而更低的值，如 0.2，会使其更加集中和确定。
     * 我们通常建议可以更改这个值或者更改 top_p，但不建议同时对两者进行修改。
     */
    @Builder.Default
    private Float temperature = 1f;

    /**
     * 作为调节采样温度的替代方案，模型会考虑前 top_p 概率的 token 的结果。所以 0.1 就意味着只有包括在最高 10% 概率中的 token 会被考虑。
     * 我们通常建议修改这个值或者更改 temperature，但不建议同时对两者进行修改。
     */
    @Builder.Default
    @JsonProperty("top_p")
    private Float topP = 1f;

    /**
     * 如果设置为 True，将会以 SSE（server-sent events）的形式以流式发送消息增量。消息流以 data: [DONE] 结尾
     */
    private Boolean stream = false;


    /**
     * 限制一次请求中模型生成 completion 的最大 token 数。输入 token 和输出 token 的总长度受模型的上下文长度的限制。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

}
