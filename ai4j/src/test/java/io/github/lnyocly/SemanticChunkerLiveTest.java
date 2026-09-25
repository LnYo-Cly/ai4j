package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.platform.ollama.embedding.OllamaEmbeddingService;
import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.rag.ingestion.SemanticTextChunker;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * SemanticTextChunker 真实 embedding 冒烟，默认跳过。
 *
 * <p>{@code OLLAMA_LIVE_TEST=1} 启用，要求本地 Ollama 已拉取
 * {@code OLLAMA_LIVE_EMBED_MODEL}（默认 hf.co/Qwen/Qwen3-Embedding-0.6B-GGUF:latest）。
 * 验证目标：断点落在主题切换处，而不是按字符数硬切。</p>
 */
@Category(LiveProviderTest.class)
public class SemanticChunkerLiveTest {

    private static final String MULTI_TOPIC_DOC =
            "平台支持买家在支付成功后 24 小时内申请退款。退款原路退回，通常 3-5 个工作日到账。"
                    + "超过 24 小时的订单需要先联系商家协商。退款到账时间以银行处理为准。"
                    + "订单 12345 当前处于已发货状态。物流显示包裹已到达杭州转运中心。"
                    + "预计明天下午送达，派送前快递会电话联系。发货后 48 小时内可在订单页查看物流轨迹。"
                    + "商户可在控制台创建优惠券。优惠券支持满减和折扣两种类型。"
                    + "创建后可以按渠道投放，也可以导出核销码。每个活动最多同时启用 10 张券。";

    @Test
    public void semanticBoundariesFollowTopicShifts() throws Exception {
        LiveProviderTestSupport.requireEnv("Skip semantic chunker live test", "OLLAMA_LIVE_TEST");
        String host = LiveProviderTestSupport.firstEnv("OLLAMA_LIVE_BASE_URL");
        if (LiveProviderTestSupport.isBlank(host)) {
            host = "http://localhost:11434/";
        }
        String model = LiveProviderTestSupport.firstEnv("OLLAMA_LIVE_EMBED_MODEL");
        if (LiveProviderTestSupport.isBlank(model)) {
            model = "hf.co/Qwen/Qwen3-Embedding-0.6B-GGUF:latest";
        }

        OllamaConfig ollamaConfig = new OllamaConfig();
        ollamaConfig.setApiHost(host);
        OllamaEmbeddingService embeddingService =
                new OllamaEmbeddingService(new Configuration(), ollamaConfig);
        SemanticTextChunker chunker = new SemanticTextChunker(embeddingService, model, 2000, 0.80);

        RagDocument document = RagDocument.builder().documentId("live-doc").build();
        List<RagChunk> chunks = chunker.chunk(document, MULTI_TOPIC_DOC);

        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("=== chunk " + chunks.get(i).getChunkIndex() + " ===");
            System.out.println(chunks.get(i).getContent());
        }

        Assert.assertTrue("expected semantic split to produce more than one chunk",
                chunks.size() >= 2);
        Assert.assertTrue("expected chunks to stay under maxChunkSize",
                chunks.stream().allMatch(c -> c.getContent().length() <= 2000));

        String joined = joinContents(chunks);
        Assert.assertEquals("semantic split must not drop or reorder content",
                MULTI_TOPIC_DOC, joined);
        Assert.assertTrue("refund topic should cluster in one chunk",
                sameChunkContains(chunks, "申请退款", "原路退回"));
        Assert.assertTrue("logistics topic should cluster in one chunk",
                sameChunkContains(chunks, "已发货状态", "转运中心"));
    }

    private String joinContents(List<RagChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        for (RagChunk chunk : chunks) {
            builder.append(chunk.getContent());
        }
        return builder.toString();
    }

    private boolean sameChunkContains(List<RagChunk> chunks, String first, String second) {
        for (RagChunk chunk : chunks) {
            if (chunk.getContent().contains(first) && chunk.getContent().contains(second)) {
                return true;
            }
        }
        return false;
    }
}
