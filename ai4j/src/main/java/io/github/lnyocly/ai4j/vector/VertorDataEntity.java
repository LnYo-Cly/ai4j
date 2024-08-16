package io.github.lnyocly.ai4j.vector;

import lombok.Data;

import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/14 18:23
 */
@Data
public class VertorDataEntity {

    /**
     * 分段后的每一段的向量
     */
    private List<List<Float>> vector;

    /**
     *  每一段的内容
     */
    private List<String> content;

    /**
     *  总共token数量
     */
    //private Integer total_token;
}
