package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.document.TikaUtil;
import io.github.lnyocly.ai4j.document.mineru.MinerUDocumentParser;
import io.github.lnyocly.ai4j.document.mineru.MinerUExtractResult;
import io.github.lnyocly.ai4j.document.mineru.MinerUService;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskStatus;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loader that parses files or remote URLs through the MinerU cloud
 * document-parsing service, yielding Markdown content.
 *
 * <p>With a configured MinerU apiKey the v4 precise pipeline is used
 * (file upload or URL task, zip result unpacked to full.md); without one it
 * falls back to the token-free v1 lite API (IP rate-limited, ≤10MB/≤20 pages).</p>
 */
public class MinerUDocumentLoader implements DocumentLoader {

    private final MinerUService service;
    private final MinerUDocumentParser parser;

    public MinerUDocumentLoader(MinerUService service) {
        this.service = service;
        this.parser = new MinerUDocumentParser(service);
    }

    @Override
    public boolean supports(IngestionSource source) {
        return source != null && (source.getFile() != null || isHttpUri(source.getUri()));
    }

    @Override
    public LoadedDocument load(IngestionSource source) throws Exception {
        File file = source.getFile();
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }

        if (file != null) {
            metadata.put("mimeType", TikaUtil.detectMimeType(file));
            metadata.put("parser", "mineru");
            try (InputStream in = new FileInputStream(file)) {
                return LoadedDocument.builder()
                        .content(parser.parse(in, file.getName()))
                        .sourceName(source.getName() == null ? file.getName() : source.getName())
                        .sourcePath(source.getPath() == null ? file.getAbsolutePath() : source.getPath())
                        .sourceUri(source.getUri() == null ? file.toURI().toString() : source.getUri())
                        .metadata(metadata)
                        .build();
            }
        }

        String uri = source.getUri();
        metadata.put("parser", "mineru");
        if (service.hasApiKey()) {
            MinerUTaskStatus status = service.submitTaskAndWait(
                    MinerUTaskRequest.builder().url(uri).build());
            MinerUExtractResult result = service.downloadAndExtract(status.getFullZipUrl());
            metadata.put("mode", "precise");
            metadata.put("taskId", status.getTaskId());
            metadata.put("imageCount", result.getImages() == null ? 0 : result.getImages().size());
            String content = result.getMarkdown();
            if (content == null) {
                throw new IllegalStateException("MinerU result zip contains no markdown for: " + uri);
            }
            return LoadedDocument.builder()
                    .content(content)
                    .sourceName(source.getName() == null ? uri : source.getName())
                    .sourcePath(source.getPath())
                    .sourceUri(uri)
                    .metadata(metadata)
                    .build();
        }
        metadata.put("mode", "lite");
        return LoadedDocument.builder()
                .content(service.liteParseByUrl(uri))
                .sourceName(source.getName() == null ? uri : source.getName())
                .sourcePath(source.getPath())
                .sourceUri(uri)
                .metadata(metadata)
                .build();
    }

    private boolean isHttpUri(String uri) {
        return StringUtils.isNotBlank(uri)
                && (uri.startsWith("http://") || uri.startsWith("https://"));
    }
}
