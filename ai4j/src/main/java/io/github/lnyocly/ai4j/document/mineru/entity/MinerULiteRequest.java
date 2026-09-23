package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v1 Agent 轻量解析请求体（免 token，IP 限频）。
 *
 * <p>{@code POST /parse/url} 使用 {@link #url}；
 * {@code POST /parse/file} 使用 {@link #fileName}（含扩展名）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MinerULiteRequest {

    /** 远程文件 URL，/parse/url 必填。 */
    private String url;

    /** 文件名（含扩展名），/parse/file 必填；/parse/url 可选用于判断类型。 */
    @JsonProperty("file_name")
    private String fileName;

    /** 解析语言，默认 ch，仅对 PDF 生效。 */
    private String language;

    /** 是否开启表格识别，默认 true，仅对 PDF 生效。 */
    @JsonProperty("enable_table")
    private Boolean enableTable;

    /** 是否开启 OCR，默认 false，仅对 PDF 生效。 */
    @JsonProperty("is_ocr")
    private Boolean isOcr;

    /** 是否开启公式识别，默认 true，仅对 PDF 生效。 */
    @JsonProperty("enable_formula")
    private Boolean enableFormula;

    /** 页码范围，仅对 PDF 有效，如 "1-10" 或 "5"。 */
    @JsonProperty("page_range")
    private String pageRange;
}
