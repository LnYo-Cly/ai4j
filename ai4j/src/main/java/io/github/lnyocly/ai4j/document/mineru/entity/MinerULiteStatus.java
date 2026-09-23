package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * v1 Agent 轻量解析任务状态：提交响应与 {@code GET /parse/{taskId}} 的 data 部分。
 *
 * <p>state：waiting-file 等待文件上传 / uploading 文件下载中 /
 * pending 排队中 / running 解析中 / done 完成 / failed 失败。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerULiteStatus {

    public static final String STATE_DONE = "done";
    public static final String STATE_FAILED = "failed";

    @JsonProperty("task_id")
    private String taskId;

    /** OSS 签名上传地址，仅 /parse/file 提交响应中返回。 */
    @JsonProperty("file_url")
    private String fileUrl;

    private String state;

    /** Markdown 结果文件 CDN 链接，state=done 时有效。 */
    @JsonProperty("markdown_url")
    private String markdownUrl;

    @JsonProperty("err_code")
    private Integer errCode;

    @JsonProperty("err_msg")
    private String errMsg;

    public boolean isDone() {
        return STATE_DONE.equals(state);
    }

    public boolean isFailed() {
        return STATE_FAILED.equals(state);
    }
}
