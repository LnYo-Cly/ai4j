package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagTrace {

    private RagQueryPlan queryPlan;

    @Builder.Default
    private List<RagHit> retrievedHits = Collections.emptyList();

    @Builder.Default
    private List<RagHit> rerankedHits = Collections.emptyList();

    /** query planning 耗时，单位毫秒。 */
    private long planningDurationMs;

    /** 召回耗时（含 query embedding / KNN / pre-filter），单位毫秒。 */
    private long retrieveDurationMs;

    /** 重排耗时（LlmReranker 时含 N 次 GLM 打分调用，是 rerank 成本的耗时 proxy），单位毫秒。 */
    private long rerankDurationMs;

    /** 上下文组装耗时（含引用标注 / truncate），单位毫秒。 */
    private long assembleDurationMs;

    /** 整个 search 总耗时（planning + retrieve + rerank + assemble），单位毫秒。 */
    private long totalDurationMs;
}
