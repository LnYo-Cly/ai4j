package io.github.lnyocly.ai4j.platform.baichuan.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.zhipu.chat.entity.ZhipuChatCompletion;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaichuanChatCompletion {

    @NonNull
    private String model;

    @NonNull
    private List<ChatMessage> messages;

    /**
     * 采样温度，控制输出的随机性，必须为正数。值越大，会使输出更随机
     * [0.0, 1.0]
     */
    private Float temperature = 0.95f;

    /**
     * 作为调节采样温度的替代方案，模型会考虑前 top_p 概率的 token 的结果。所以 0.1 就意味着只有包括在最高 10% 概率中的 token 会被考虑。
     * 我们通常建议修改这个值或者更改 temperature，但不建议同时对两者进行修改。
     */
    @Builder.Default
    @JsonProperty("top_p")
    private Float topP = 1f;

    /**
     * 限制一次请求中模型生成 completion 的最大 token 数。输入 token 和输出 token 的总长度受模型的上下文长度的限制。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<String> stop;

    private List<Tool> tools;

    /**
     * 辅助属性
     */
    @JsonIgnore
    private List<String> functions;

    @JsonProperty("tool_choice")
    private String toolChoice;

    /**
     * 如果设置为 True，将会以 SSE（server-sent events）的形式以流式发送消息增量。消息流以 data: [DONE] 结尾
     */
    private Boolean stream = false;

    public static class BaichuanCompletionBuilder {
        private List<String> functions;

        public BaichuanChatCompletion.BaichuanCompletionBuilder functions(String... functions) {
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            this.functions.addAll(Arrays.asList(functions));
            return this;
        }

        public BaichuanChatCompletion.BaichuanCompletionBuilder functions(List<String> functions) {
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            this.functions.addAll(functions);
            return this;
        }


    }
}
