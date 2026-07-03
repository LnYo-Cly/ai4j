package io.github.lnyocly.ai4j.rag.ingestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-stage timing for {@link IngestionPipeline#ingest}, so production ingestion debugging is no
 * longer limited to a single "Ingested N chunks" log line. When a PDF takes 40s to ingest, this
 * tells you whether the time went to parsing, chunking, embedding, or upsert.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionTrace {

    /** 文档加载 + processor（解析 / OCR / 清洗）耗时，毫秒。 */
    private long loadDurationMs;

    /** 切块耗时，毫秒。 */
    private long chunkDurationMs;

    /** 向量化耗时（含所有 embedding batch 调用，是 ingest 成本大头），毫秒。 */
    private long embedDurationMs;

    /** 入库（{@code vectorStore.upsert}）耗时，毫秒。 */
    private long upsertDurationMs;

    /** 整个 ingest 总耗时（load + chunk + embed + upsert），毫秒。 */
    private long totalDurationMs;

    /** 切块数量。 */
    private int chunkCount;
}
