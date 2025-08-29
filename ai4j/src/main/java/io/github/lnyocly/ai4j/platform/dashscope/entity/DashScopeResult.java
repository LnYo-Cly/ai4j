package io.github.lnyocly.ai4j.platform.dashscope.entity;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import lombok.Data;

@Data
public class DashScopeResult {

    private String model;

    /**
     * 创建聊天完成时的 Unix 时间戳（以秒为单位）。
     */
    private Long created;
    /**
     * 对象的类型, 其值为 chat.completion 或 chat.completion.chunk
     */
    private String object;

    private GenerationResult generationResult;
}
