package io.github.lnyocly;

import io.github.lnyocly.ai4j.platform.standard.rerank.StandardRerankService;
import io.github.lnyocly.ai4j.rerank.entity.RerankDocument;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.rerank.entity.RerankResult;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.List;

/**
 * StandardRerankService 真实端点冒烟，默认跳过。
 *
 * <p>{@code SF_RERANK_LIVE_TEST=1} + {@code SILICONFLOW_API_KEY=sk-...} 启用。
 * 硅基流动 {@code /v1/rerank} 与 Jina 同协议（model/query/documents/top_n →
 * results[].relevance_score），覆盖 JinaRerankService 的同一条代码路径。</p>
 */
@Category(LiveProviderTest.class)
public class RerankLiveTest {

    @Test
    public void standardRerankAgainstSiliconFlow() throws Exception {
        LiveProviderTestSupport.requireEnv("Skip rerank live test", "SF_RERANK_LIVE_TEST");
        String apiKey = LiveProviderTestSupport.requireEnv("Skip rerank live test",
                "SILICONFLOW_API_KEY");
        String model = LiveProviderTestSupport.firstEnv("SF_RERANK_MODEL");
        if (LiveProviderTestSupport.isBlank(model)) {
            model = "BAAI/bge-reranker-v2-m3";
        }

        Configuration configuration = new Configuration();
        StandardRerankService service = new StandardRerankService(
                configuration.getOkHttpClient() == null ? new OkHttpClient()
                        : configuration.getOkHttpClient(),
                "https://api.siliconflow.cn", null, "v1/rerank");

        RerankRequest request = RerankRequest.builder()
                .model(model)
                .query("退货政策")
                .topN(2)
                .returnDocuments(Boolean.TRUE)
                .documents(Arrays.asList(
                        doc("退货签收后7天内无理由"),
                        doc("物流配送时效说明"),
                        doc("退款原路退回3-5个工作日"),
                        doc("VIP会员权益介绍")))
                .build();

        RerankResponse response = service.rerank(null, apiKey, request);
        Assert.assertNotNull(response);
        List<RerankResult> results = response.getResults();
        Assert.assertEquals(2, results.size());

        RerankResult top = results.get(0);
        Assert.assertNotNull(top.getRelevanceScore());
        Assert.assertNotNull(top.getIndex());
        Assert.assertTrue("top hit should be the refund doc",
                top.getIndex() == 2 || top.getIndex() == 0);
        Assert.assertTrue("scores should be sorted desc",
                results.get(0).getRelevanceScore() >= results.get(1).getRelevanceScore());
        Assert.assertNotNull("return_documents should echo text", top.getDocument());
        Assert.assertNotNull(top.getDocument().getText());
        Assert.assertNotNull("usage should be parsed", response.getUsage());
    }

    private RerankDocument doc(String text) {
        return RerankDocument.builder().text(text).build();
    }
}
