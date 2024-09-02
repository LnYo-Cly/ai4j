package io.github.lnyocly.ai4j.platform.hunyuan.chat.entity;

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
 * @Description 腾讯混元响应实体类
 * @Date 2024/8/30 19:27
 */

@Data
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HunyuanChatCompletionResponse {
    /**
     * Unix 时间戳，单位为秒。
     */
    private String created;

    /**
     * Token 统计信息。
     * 按照总 Token 数量计费。
     */
    private Usage usage;

    /**
     * 免责声明。
     * 示例值：以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记
     */
    private String note;

    /**
     * 	本次请求的 RequestId。
     */
    private String id;

    /**
     * 回复内容
     */
    private List<Choice> choices;

    @JsonProperty("request_id")
    private String requestId;

    // 下面为额外补充
    private String object;
    private String model;
}
