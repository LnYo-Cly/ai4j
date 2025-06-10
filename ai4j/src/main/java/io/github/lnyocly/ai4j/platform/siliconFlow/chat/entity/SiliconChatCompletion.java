package io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.StreamOptions;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiliconChatCompletion {

    /**
     * 对话模型
     */
    @NonNull
    private String model;

    /**
     * 消息内容
     */
    @NonNull
    @Singular
    // 修改Silicon对应的messages的格式
    private List<SiliconMessage> messages;

    /**
     * 是否流式输出
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * 限制一次请求中模型生成 completion 的最大 token 数。输入 token 和输出 token 的总长度受模型的上下文长度的限制。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens = 512;

    /**
     * 在思考模式和非思考模式之间切换。默认值为 True。此字段仅适用于 Qwen3。
     */
    @Builder.Default
    private Boolean enable_thinking = false;


    /**
     * 推理模型的思维链输出的最大token数。该字段适用于所有推理模型。 128 <= x <= 32768
     */
    private Integer thinking_budget = 4096;


    /**
     * 最多4个序列，API将在这些序列处停止生成后续标记。返回的文本将不包含停止序列。
     */
    private List<String> stop;

    /**
     * 采样温度，介于 0 和 2 之间。更高的值，如 0.8，会使输出更随机，而更低的值，如 0.2，会使其更加集中和确定。
     * 我们通常建议可以更改这个值或者更改 top_p，但不建议同时对两者进行修改。
     */
    @Builder.Default
    private Float temperature = 0.7f;

    /**
     * 作为调节采样温度的替代方案，模型会考虑前 top_p 概率的 token 的结果。所以 0.1 就意味着只有包括在最高 10% 概率中的 token 会被考虑。
     * 我们通常建议修改这个值或者更改 temperature，但不建议同时对两者进行修改。
     */
    @Builder.Default
    @JsonProperty("top_p")
    private Float topP = 0.7f;

    /**
     * 介于 -2.0 和 2.0 之间的数字。如果该值为正，那么新 token 会根据其在已有文本中的出现频率受到相应的惩罚，降低模型重复相同内容的可能性。
     */
    @Builder.Default
    @JsonProperty("frequency_penalty")
    private Float frequencyPenalty = 0.5f;

    /**
     * Number of generations to return
     */
    @Builder.Default
    private Integer n = 1;

    /**
     * 模型可能会调用的 tool 的列表。目前，仅支持 function 作为工具。使用此参数来提供以 JSON 作为输入参数的 function 列表。
     */
    private List<Tool> tools;

    /**
     * 一个 object，指定模型必须输出的格式。
     *
     * 设置为 { "type": "json_object" } 以启用 JSON 模式，该模式保证模型生成的消息是有效的 JSON。
     *
     * 注意: 使用 JSON 模式时，你还必须通过系统或用户消息指示模型生成 JSON。
     * 否则，模型可能会生成不断的空白字符，直到生成达到令牌限制，从而导致请求长时间运行并显得“卡住”。
     * 此外，如果 finish_reason="length"，这表示生成超过了 max_tokens 或对话超过了最大上下文长度，消息内容可能会被部分截断。
     */
    @JsonProperty("response_format")
    private Object responseFormat;


}