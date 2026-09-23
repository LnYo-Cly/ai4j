package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * v4 任务状态：单任务查询（{@code GET /extract/task/{id}}）与
 * 批量结果项（{@code extract_result[]} 元素）共用结构。
 *
 * <p>state：done 完成 / pending 排队中 / running 解析中 /
 * converting 格式转换中 / waiting-file 等待文件上传 / failed 失败。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerUTaskStatus {

    public static final String STATE_DONE = "done";
    public static final String STATE_FAILED = "failed";

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("data_id")
    private String dataId;

    /** 批量结果项中的文件名。 */
    @JsonProperty("file_name")
    private String fileName;

    private String state;

    /** 解析结果压缩包地址，state=done 时有效。 */
    @JsonProperty("full_zip_url")
    private String fullZipUrl;

    /** 失败原因，state=failed 时有效。 */
    @JsonProperty("err_msg")
    private String errMsg;

    @JsonProperty("extract_progress")
    private ExtractProgress extractProgress;

    public boolean isDone() {
        return STATE_DONE.equals(state);
    }

    public boolean isFailed() {
        return STATE_FAILED.equals(state);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractProgress {
        @JsonProperty("extracted_pages")
        private Integer extractedPages;

        @JsonProperty("total_pages")
        private Integer totalPages;

        @JsonProperty("start_time")
        private String startTime;
    }
}
