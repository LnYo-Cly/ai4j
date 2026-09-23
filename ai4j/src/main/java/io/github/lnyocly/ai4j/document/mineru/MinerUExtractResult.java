package io.github.lnyocly.ai4j.document.mineru;

import lombok.Builder;
import lombok.Data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU v4 解析结果压缩包的解包视图。
 *
 * <p>非 html 文件的 zip 通常包含：{@code full.md}（Markdown 解析结果）、
 * {@code *_content_list.json}（内容列表）、{@code *_model.json}、
 * {@code layout.json/middle.json} 以及 {@code images/} 下的图片；
 * html 文件则包含 {@code full.md} 与 {@code main.html}。</p>
 */
@Data
@Builder
public class MinerUExtractResult {

    /** Markdown 解析结果（full.md），zip 中不存在时为 null。 */
    private String markdown;

    /** *_content_list.json 内容，不存在时为 null。 */
    private String contentListJson;

    /** images/ 目录下的图片，键为 zip 内路径。 */
    @Builder.Default
    private Map<String, byte[]> images = new LinkedHashMap<String, byte[]>();

    /** zip 内全部条目，键为 zip 内路径，供高级用户自取 model.json 等。 */
    @Builder.Default
    private Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();

    /**
     * 将 zip 字节解包为结构化结果。
     *
     * @param zipBytes zip 文件字节
     * @return 解包结果
     * @throws IOException zip 读取失败时
     */
    public static MinerUExtractResult fromZip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zin.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                entries.put(entry.getName(), out.toByteArray());
            }
        }

        String markdown = null;
        String contentListJson = null;
        Map<String, byte[]> images = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey();
            String lower = name.toLowerCase();
            if (lower.endsWith("full.md")) {
                markdown = new String(e.getValue(), StandardCharsets.UTF_8);
            } else if (markdown == null && lower.endsWith(".md")) {
                markdown = new String(e.getValue(), StandardCharsets.UTF_8);
            }
            if (lower.endsWith("content_list.json")) {
                contentListJson = new String(e.getValue(), StandardCharsets.UTF_8);
            }
            if (lower.startsWith("images/") || isImageName(lower)) {
                images.put(name, e.getValue());
            }
        }

        return MinerUExtractResult.builder()
                .markdown(markdown)
                .contentListJson(contentListJson)
                .images(images)
                .entries(entries)
                .build();
    }

    private static boolean isImageName(String lowerName) {
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp");
    }
}
