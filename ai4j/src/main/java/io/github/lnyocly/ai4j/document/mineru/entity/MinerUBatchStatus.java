package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * v4 批量任务查询结果：{@code GET /extract-results/batch/{batchId}} 的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerUBatchStatus {

    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("extract_result")
    private List<MinerUTaskStatus> extractResult;

    /** 全部文件均进入终态（done/failed）时返回 true。 */
    public boolean isFinished() {
        if (extractResult == null || extractResult.isEmpty()) {
            return false;
        }
        for (MinerUTaskStatus item : extractResult) {
            if (!item.isDone() && !item.isFailed()) {
                return false;
            }
        }
        return true;
    }
}
