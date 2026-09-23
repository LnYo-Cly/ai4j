package io.github.lnyocly;

import io.github.lnyocly.ai4j.document.mineru.MinerUConfig;
import io.github.lnyocly.ai4j.document.mineru.MinerUExtractResult;
import io.github.lnyocly.ai4j.document.mineru.MinerUService;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskStatus;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.charset.StandardCharsets;

/**
 * MinerU 真实接口冒烟测试。
 *
 * <ul>
 *   <li>lite 链路免 token，设 {@code MINERU_LIVE_TEST=1} 启用（IP 限频，谨慎重跑）。</li>
 *   <li>v4 链路设 {@code MINERU_API_KEY=sk-...} 启用。</li>
 * </ul>
 */
@Category(LiveProviderTest.class)
public class MinerULiveTest {

    private static final String DEMO_PDF_URL = "https://cdn-mineru.openxlab.org.cn/demo/example.pdf";

    /** 一页最小 PDF，足以触发解析链路。 */
    private static final byte[] TINY_PDF = (
            "%PDF-1.4\n"
                    + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                    + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                    + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R"
                    + "/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                    + "4 0 obj<</Length 44>>stream\n"
                    + "BT /F1 24 Tf 100 700 Td (Hello MinerU) Tj ET\n"
                    + "endstream\nendobj\n"
                    + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
                    + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(StandardCharsets.UTF_8);

    @Test
    public void liteParseByUrlReturnsMarkdown() throws Exception {
        LiveProviderTestSupport.requireEnv(
                "Skip MinerU lite live test", "MINERU_LIVE_TEST");
        MinerUService service = new MinerUService(new MinerUConfig(), new OkHttpClient());
        String markdown = service.liteParseByUrl(DEMO_PDF_URL);
        Assert.assertNotNull(markdown);
        Assert.assertFalse(markdown.trim().isEmpty());
    }

    @Test
    public void v4UrlTaskAndZipExtract() throws Exception {
        String apiKey = LiveProviderTestSupport.requireEnv(
                "Skip MinerU v4 live test", "MINERU_API_KEY", "MINERU_TOKEN");
        MinerUService service = service(apiKey);
        MinerUTaskStatus status = service.submitTaskAndWait(
                MinerUTaskRequest.builder().url(DEMO_PDF_URL).build());
        Assert.assertTrue(status.isDone());
        Assert.assertNotNull(status.getFullZipUrl());
        MinerUExtractResult result = service.downloadAndExtract(status.getFullZipUrl());
        Assert.assertNotNull(result.getMarkdown());
        Assert.assertFalse(result.getMarkdown().trim().isEmpty());
    }

    @Test
    public void v4UploadAndWait() throws Exception {
        String apiKey = LiveProviderTestSupport.requireEnv(
                "Skip MinerU v4 live test", "MINERU_API_KEY", "MINERU_TOKEN");
        MinerUService service = service(apiKey);
        MinerUTaskStatus status = service.uploadAndWait("ai4j-mineru-live.pdf", TINY_PDF);
        Assert.assertTrue(status.isDone());
        Assert.assertNotNull(status.getFullZipUrl());
        MinerUExtractResult result = service.downloadAndExtract(status.getFullZipUrl());
        Assert.assertNotNull(result.getMarkdown());
    }

    private static MinerUService service(String apiKey) {
        MinerUConfig config = new MinerUConfig();
        config.setApiKey(apiKey);
        config.setPollIntervalMs(5000);
        config.setPollTimeoutMs(600000);
        return new MinerUService(config, new OkHttpClient());
    }
}
