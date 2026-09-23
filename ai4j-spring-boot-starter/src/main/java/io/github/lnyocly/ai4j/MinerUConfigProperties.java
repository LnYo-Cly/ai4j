package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MinerU 文档解析服务配置。
 *
 * <p>{@code apiKey} 为空时仅可使用免 token 的 Agent 轻量解析接口
 * （IP 限频，≤10MB/≤20 页，仅输出 Markdown）。</p>
 */
@Data
@ConfigurationProperties(prefix = "ai.mineru")
public class MinerUConfigProperties {

    /** MinerU API token（API 管理页面创建）。 */
    private String apiKey;

    /** v4 精准解析 API 根地址。 */
    private String baseUrl = "https://mineru.net/api/v4";

    /** v1 Agent 轻量解析 API 根地址（免 token）。 */
    private String liteBaseUrl = "https://mineru.net/api/v1/agent";

    /** 模型版本：pipeline / vlm / MinerU-HTML。 */
    private String modelVersion = "vlm";

    /** 是否启动 OCR。 */
    private Boolean isOcr = false;

    /** 是否开启公式识别。 */
    private Boolean enableFormula = true;

    /** 是否开启表格识别。 */
    private Boolean enableTable = true;

    /** 文档语言。 */
    private String language = "ch";

    /** 页码范围，如 "2,4-6"。 */
    private String pageRanges;

    /** 额外导出格式：docx/html/latex。 */
    private List<String> extraFormats;

    /** 业务数据 ID 透传字段（data_id）。 */
    private String dataId;

    /** 轮询间隔（毫秒）。 */
    private long pollIntervalMs = 3000;

    /** 轮询总超时（毫秒）。 */
    private long pollTimeoutMs = 600000;
}
