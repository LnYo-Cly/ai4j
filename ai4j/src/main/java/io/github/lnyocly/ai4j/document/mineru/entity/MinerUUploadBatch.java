package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * v4 本地文件上传链接申请结果：{@code POST /file-urls/batch} 的 data 部分。
 * {@link #fileUrls} 与请求中的 files 顺序一一对应，24 小时内有效。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerUUploadBatch {

    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("file_urls")
    private List<String> fileUrls;
}
