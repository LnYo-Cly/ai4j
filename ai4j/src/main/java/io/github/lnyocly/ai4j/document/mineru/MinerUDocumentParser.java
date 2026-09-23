package io.github.lnyocly.ai4j.document.mineru;

import io.github.lnyocly.ai4j.document.DocumentParser;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.service.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 MinerU 云端解析服务的 {@link DocumentParser} 实现。
 *
 * <p>解析策略：</p>
 * <ul>
 *   <li>配置了 apiKey（或环境变量 {@code MINERU_API_KEY}/{@code MINERU_TOKEN}）时走 v4
 *       精准解析链：申请上传链接 → PUT 上传 → 轮询 → 解包 zip 取 full.md（≤200MB/≤200 页）。</li>
 *   <li>未配置时走免 token 的 v1 Agent 轻量解析链（IP 限频，≤10MB/≤20 页，仅输出 Markdown）。</li>
 * </ul>
 *
 * <p>本实现<strong>不通过 META-INF/services 注册</strong>——远端服务需要显式配置，
 * 请通过构造器注入后使用，避免与 classpath 上其他 DocumentParser 实现的
 * ServiceLoader 首命中选择产生不确定性。</p>
 */
public class MinerUDocumentParser implements DocumentParser {

    /** v4 精准解析单文件大小上限。 */
    public static final long MAX_PRECISE_BYTES = 200L * 1024 * 1024;

    /** v1 轻量解析单文件大小上限。 */
    public static final long MAX_LITE_BYTES = 10L * 1024 * 1024;

    private static final Tika TIKA = new Tika();

    private static final Map<String, String> MIME_EXTENSIONS = new HashMap<String, String>();
    static {
        MIME_EXTENSIONS.put("application/pdf", ".pdf");
        MIME_EXTENSIONS.put("application/msword", ".doc");
        MIME_EXTENSIONS.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        MIME_EXTENSIONS.put("application/vnd.ms-powerpoint", ".ppt");
        MIME_EXTENSIONS.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
        MIME_EXTENSIONS.put("application/vnd.ms-excel", ".xls");
        MIME_EXTENSIONS.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
        MIME_EXTENSIONS.put("text/html", ".html");
        MIME_EXTENSIONS.put("image/png", ".png");
        MIME_EXTENSIONS.put("image/jpeg", ".jpg");
        MIME_EXTENSIONS.put("image/jp2", ".jp2");
        MIME_EXTENSIONS.put("image/webp", ".webp");
        MIME_EXTENSIONS.put("image/gif", ".gif");
        MIME_EXTENSIONS.put("image/bmp", ".bmp");
    }

    private final MinerUService service;

    /**
     * 以环境变量构造：读取 {@code MINERU_API_KEY} / {@code MINERU_TOKEN}，
     * 未设置时仅可用轻量解析链。
     */
    public MinerUDocumentParser() {
        this(defaultService());
    }

    public MinerUDocumentParser(MinerUService service) {
        this.service = service;
    }

    public MinerUDocumentParser(Configuration configuration) {
        this(new MinerUService(configuration));
    }

    private static MinerUService defaultService() {
        MinerUConfig config = new MinerUConfig();
        String apiKey = System.getenv("MINERU_API_KEY");
        if (StringUtils.isBlank(apiKey)) {
            apiKey = System.getenv("MINERU_TOKEN");
        }
        config.setApiKey(apiKey);
        return new MinerUService(config);
    }

    /**
     * 解析文档为 Markdown 文本。
     *
     * @param in       文档输入流，方法内消费完毕
     * @param filename 文件名提示（含后缀），可为 {@code null}；为空时按内容探测 MIME 补后缀
     * @return MinerU 解析出的 Markdown 文本
     * @throws IOException 读取、超限或云端解析失败时
     */
    @Override
    public String parse(InputStream in, String filename) throws IOException {
        byte[] bytes = readAll(in);
        boolean precise = service.hasApiKey();
        long limit = precise ? MAX_PRECISE_BYTES : MAX_LITE_BYTES;
        if (bytes.length > limit) {
            throw new IOException("Document size " + bytes.length + " bytes exceeds MinerU "
                    + (precise ? "precise" : "lite") + " limit of " + limit + " bytes");
        }
        if (bytes.length == 0) {
            throw new IOException("Document is empty");
        }
        String effectiveName = resolveFilename(filename, bytes);
        try {
            if (precise) {
                MinerUExtractResult result = service.downloadAndExtract(
                        service.uploadAndWait(effectiveName, bytes).getFullZipUrl());
                String markdown = result.getMarkdown();
                if (markdown == null) {
                    throw new IOException("MinerU result zip contains no markdown for: " + effectiveName);
                }
                return markdown;
            }
            return service.liteParseByFile(effectiveName, bytes);
        } catch (Ai4jException e) {
            throw new IOException("MinerU parse failed for " + effectiveName + ": " + e.getMessage(), e);
        }
    }

    private String resolveFilename(String filename, byte[] bytes) {
        if (StringUtils.isNotBlank(filename)) {
            return filename;
        }
        String mime;
        try {
            mime = TIKA.detect(bytes);
        } catch (Exception e) {
            mime = null;
        }
        String ext = mime == null ? null : MIME_EXTENSIONS.get(mime);
        return "document" + (ext == null ? ".pdf" : ext);
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
