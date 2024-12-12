package io.github.lnyocly.ai4j.websearch.searxng;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * @Author cly
 * @Description SearXNG网路搜索配置信息
 * @Date 2024/12/11 23:05
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearXNGConfig {
    private String url;
}
