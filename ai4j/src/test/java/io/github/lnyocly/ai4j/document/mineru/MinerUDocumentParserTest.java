package io.github.lnyocly.ai4j.document.mineru;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class MinerUDocumentParserTest {

    @Test
    public void parseWithoutApiKeyUsesLiteChain() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String uploadUrl = server.url("/oss/lite1").toString();
        String mdUrl = server.url("/md/full.md").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"file_url\":\""
                + uploadUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"state\":\"done\",\"markdown_url\":\""
                + mdUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("# Parsed\n"));
        try {
            MinerUService service = service(server, false);
            String markdown = new MinerUDocumentParser(service)
                    .parse(new ByteArrayInputStream("doc".getBytes(StandardCharsets.UTF_8)), "a.pdf");

            Assert.assertEquals("# Parsed\n", markdown);
            RecordedRequest submit = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v1/agent/parse/file", submit.getPath());
            Assert.assertNull(submit.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void parseWithApiKeyUsesPreciseChain() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String uploadUrl = server.url("/oss/f1").toString();
        String zipUrl = server.url("/r.zip").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"file_urls\":[\""
                + uploadUrl + "\"]},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"extract_result\":["
                + "{\"file_name\":\"a.pdf\",\"state\":\"done\",\"full_zip_url\":\"" + zipUrl + "\"}]},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody(new okio.Buffer().write(MinerUServiceTest.zipBytes())));
        try {
            MinerUService service = service(server, true);
            String markdown = new MinerUDocumentParser(service)
                    .parse(new ByteArrayInputStream("doc".getBytes(StandardCharsets.UTF_8)), "a.pdf");

            Assert.assertEquals("# Doc\n", markdown);
            RecordedRequest apply = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v4/file-urls/batch", apply.getPath());
            Assert.assertEquals("Bearer test-key", apply.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void parseRejectsEmptyInput() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            MinerUService service = service(server, false);
            new MinerUDocumentParser(service).parse(new ByteArrayInputStream(new byte[0]), "a.pdf");
            Assert.fail("expected IOException");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("empty"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void parseRejectsOverLiteLimit() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            MinerUService service = service(server, false);
            byte[] big = new byte[(int) (MinerUDocumentParser.MAX_LITE_BYTES + 1)];
            new MinerUDocumentParser(service).parse(new ByteArrayInputStream(big), "big.pdf");
            Assert.fail("expected IOException");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("exceeds"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void parseDerivesFilenameFromContent() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String uploadUrl = server.url("/oss/lite1").toString();
        String mdUrl = server.url("/md/full.md").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"file_url\":\""
                + uploadUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"state\":\"done\",\"markdown_url\":\""
                + mdUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("# ok"));
        try {
            MinerUService service = service(server, false);
            // filename 为 null 时按内容探测：%PDF- 前缀应得到 .pdf 后缀
            byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
            new MinerUDocumentParser(service).parse(new ByteArrayInputStream(pdf), null);

            RecordedRequest submit = server.takeRequest(1, TimeUnit.SECONDS);
            String body = submit.getBody().readUtf8();
            Assert.assertTrue(body.contains("\"file_name\":\"document.pdf\""));
        } finally {
            server.shutdown();
        }
    }

    private static MinerUService service(MockWebServer server, boolean withKey) {
        MinerUConfig config = new MinerUConfig();
        config.setBaseUrl(server.url("/api/v4").toString());
        config.setLiteBaseUrl(server.url("/api/v1/agent").toString());
        config.setPollIntervalMs(10);
        config.setPollTimeoutMs(5000);
        if (withKey) {
            config.setApiKey("test-key");
        }
        return new MinerUService(config, new OkHttpClient());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
