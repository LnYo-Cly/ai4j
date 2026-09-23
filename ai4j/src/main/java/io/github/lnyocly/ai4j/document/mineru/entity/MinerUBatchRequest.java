package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/**
 * v4 批量接口共用请求体：
 * {@code POST /extract/task/batch}（files 填 url）与
 * {@code POST /file-urls/batch}（files 填 name，申请上传链接）。
 * 单次最多 50 个文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MinerUBatchRequest {

    @Singular
    private List<MinerUFileSpec> files;

    @JsonProperty("enable_formula")
    private Boolean enableFormula;

    @JsonProperty("enable_table")
    private Boolean enableTable;

    private String language;

    @JsonProperty("model_version")
    private String modelVersion;

    private String callback;

    private String seed;

    @JsonProperty("extra_formats")
    private List<String> extraFormats;
}
