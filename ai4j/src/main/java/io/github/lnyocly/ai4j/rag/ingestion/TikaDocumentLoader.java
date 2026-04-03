package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.document.TikaUtil;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loader for local files using Apache Tika.
 */
public class TikaDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(IngestionSource source) {
        return source != null && source.getFile() != null;
    }

    @Override
    public LoadedDocument load(IngestionSource source) throws Exception {
        File file = source.getFile();
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("mimeType", TikaUtil.detectMimeType(file));
        return LoadedDocument.builder()
                .content(TikaUtil.parseFile(file))
                .sourceName(source.getName() == null ? file.getName() : source.getName())
                .sourcePath(source.getPath() == null ? file.getAbsolutePath() : source.getPath())
                .sourceUri(source.getUri() == null ? file.toURI().toString() : source.getUri())
                .metadata(metadata)
                .build();
    }
}
