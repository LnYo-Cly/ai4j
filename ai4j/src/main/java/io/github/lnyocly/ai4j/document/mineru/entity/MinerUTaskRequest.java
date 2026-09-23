package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v4 精准解析「创建解析任务」请求体：{@code POST /extract/task}。
 * 文件以公网可达 URL 形式提交，不支持直接上传文件内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MinerUTaskRequest {

    /** 文件 URL（必填），支持 pdf/doc/docx/ppt/pptx/xls/xlsx/图片/html。 */
    private String url;

    /** 是否启动 OCR，默认 false（pipeline/vlm 有效）。 */
    @JsonProperty("is_ocr")
    private Boolean isOcr;

    /** 是否开启公式识别，默认 true（pipeline/vlm 有效）。 */
    @JsonProperty("enable_formula")
    private Boolean enableFormula;

    /** 是否开启表格识别，默认 true（pipeline/vlm 有效）。 */
    @JsonProperty("enable_table")
    private Boolean enableTable;

    /** 文档语言，默认 ch。 */
    private String language;

    /** 业务数据 ID 透传，≤128 字符。 */
    @JsonProperty("data_id")
    private String dataId;

    /** 结果回调 URL；为空则需轮询任务结果。 */
    private String callback;

    /** 回调签名校验随机串；使用 callback 时必填。 */
    private String seed;

    /** 额外导出格式，仅支持 docx/html/latex 中的一个或多个。 */
    @JsonProperty("extra_formats")
    private List<String> extraFormats;

    /** 页码范围，如 "2,4-6"。 */
    @JsonProperty("page_ranges")
    private String pageRanges;

    /** 模型版本：pipeline / vlm / MinerU-HTML。 */
    @JsonProperty("model_version")
    private String modelVersion;

    /** 是否绕过 URL 内容缓存，默认 false。 */
    @JsonProperty("no_cache")
    private Boolean noCache;

    /** URL 内容缓存容忍时间（秒），默认 900。 */
    @JsonProperty("cache_tolerance")
    private Integer cacheTolerance;
}
