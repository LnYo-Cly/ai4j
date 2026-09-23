package io.github.lnyocly.ai4j.document.mineru;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MinerU 文档解析服务配置。
 *
 * <p>{@code apiKey} 为空时仅可使用免 token 的 Agent 轻量解析 API
 * （{@code liteBaseUrl}，IP 限频，单文件 ≤10MB/≤20 页，仅输出 Markdown）；
 * 配置后可使用 v4 精准解析 API（≤200MB/≤200 页，zip 含 Markdown/JSON/图片）。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MinerUConfig {

    /** MinerU API token（API 管理页面创建）；为空则只可用 lite 接口。 */
    private String apiKey;

    /** v4 精准解析 API 根地址。 */
    private String baseUrl = "https://mineru.net/api/v4";

    /** v1 Agent 轻量解析 API 根地址（免 token，IP 限频）。 */
    private String liteBaseUrl = "https://mineru.net/api/v1/agent";

    /** 模型版本：pipeline（默认）/ vlm（推荐）/ MinerU-HTML（仅 html 文件）。 */
    private String modelVersion = "vlm";

    /** 是否启动 OCR，默认 false。 */
    private Boolean isOcr = false;

    /** 是否开启公式识别，默认 true。 */
    private Boolean enableFormula = true;

    /** 是否开启表格识别，默认 true。 */
    private Boolean enableTable = true;

    /** 文档语言，默认 ch。 */
    private String language = "ch";

    /** 页码范围，例如 "2,4-6"；为空解析全部页。 */
    private String pageRanges;

    /** 额外导出格式，仅支持 docx/html/latex；markdown、json 为默认输出无需设置。 */
    private List<String> extraFormats;

    /** 业务数据 ID 透传字段（data_id）。 */
    private String dataId;

    /** 轮询间隔（毫秒）。 */
    private long pollIntervalMs = 3000;

    /** 轮询总超时（毫秒）。 */
    private long pollTimeoutMs = 600000;
}
