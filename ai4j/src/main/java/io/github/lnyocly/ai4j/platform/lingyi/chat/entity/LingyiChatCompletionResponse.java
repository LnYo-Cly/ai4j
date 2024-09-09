package io.github.lnyocly.ai4j.platform.lingyi.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description 零一万物对话响应实体
 * @Date 2024/9/9 23:02
 */
@Data
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LingyiChatCompletionResponse {
    /**
     * 该对话的唯一标识符。
     */
    private String id;

    /**
     * 对象的类型, 其值为 chat.completion 或 chat.completion.chunk
     */
    private String object;

    /**
     * 创建聊天完成时的 Unix 时间戳（以秒为单位）。
     */
    private Long created;

    /**
     * 生成该 completion 的模型名。
     */
    private String model;

    /**
     * 模型生成的 completion 的选择列表。
     */
    private List<Choice> choices;

    /**
     * 该对话补全请求的用量信息。
     */
    private Usage usage;

}
